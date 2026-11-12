// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

// Package certs loads or creates the client application certificate and
// checks server certificates against a trust list.
package certs

import (
	"bytes"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"fmt"
	"math/big"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// File names inside the certificate directory.
const (
	CertFileName = "opcua-bridge-cert.der"
	KeyFileName  = "opcua-bridge-key.pem"
	TrustedDir   = "trusted"
	RejectedDir  = "rejected"
)

// KeyBits is the RSA key size of created certificates.
const KeyBits = 2048

// Validity of created certificates.
const Validity = 20 * 365 * 24 * time.Hour

// LoadFiles loads a certificate and private key from PEM or DER files.
func LoadFiles(certFile, keyFile string) ([]byte, *rsa.PrivateKey, error) {
	cb, err := os.ReadFile(certFile)
	if err != nil {
		return nil, nil, fmt.Errorf("cannot read certificate: %v", err)
	}
	cert, err := ParseCertificate(cb)
	if err != nil {
		return nil, nil, fmt.Errorf("%s: %v", certFile, err)
	}
	kb, err := os.ReadFile(keyFile)
	if err != nil {
		return nil, nil, fmt.Errorf("cannot read private key: %v", err)
	}
	key, err := ParsePrivateKey(kb)
	if err != nil {
		return nil, nil, fmt.Errorf("%s: %v", keyFile, err)
	}
	return cert, key, nil
}

// ParseCertificate returns the DER bytes of a PEM or DER certificate.
func ParseCertificate(b []byte) ([]byte, error) {
	der := b
	if blk, _ := pem.Decode(b); blk != nil {
		der = blk.Bytes
	}
	if _, err := x509.ParseCertificate(der); err != nil {
		return nil, fmt.Errorf("invalid certificate: %v", err)
	}
	return der, nil
}

// ParsePrivateKey parses an RSA private key in PEM or DER, PKCS#1 or PKCS#8.
func ParsePrivateKey(b []byte) (*rsa.PrivateKey, error) {
	der := b
	if blk, _ := pem.Decode(b); blk != nil {
		der = blk.Bytes
	}
	if k, err := x509.ParsePKCS1PrivateKey(der); err == nil {
		return k, nil
	}
	k, err := x509.ParsePKCS8PrivateKey(der)
	if err != nil {
		return nil, errors.New("invalid private key (RSA PKCS#1 or PKCS#8 expected)")
	}
	rk, ok := k.(*rsa.PrivateKey)
	if !ok {
		return nil, errors.New("private key is not an RSA key")
	}
	return rk, nil
}

// LoadOrCreate returns the bridge's own certificate from dir, creating a
// self-signed one for appURI on first use. created reports whether a new
// certificate was written.
func LoadOrCreate(dir, appURI, appName string) (cert []byte, key *rsa.PrivateKey, created bool, err error) {
	if dir == "" {
		return nil, nil, false, errors.New("no certificate directory")
	}
	certPath := filepath.Join(dir, CertFileName)
	keyPath := filepath.Join(dir, KeyFileName)
	if exists(certPath) && exists(keyPath) {
		cert, key, err = LoadFiles(certPath, keyPath)
		if err != nil {
			return nil, nil, false, err
		}
		return cert, key, false, nil
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, nil, false, fmt.Errorf("cannot create certificate directory: %v", err)
	}
	cert, key, err = Create(appURI, appName)
	if err != nil {
		return nil, nil, false, err
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return nil, nil, false, err
	}
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER})
	if err := writeFile(keyPath, keyPEM, 0o600); err != nil {
		return nil, nil, false, fmt.Errorf("cannot write private key: %v", err)
	}
	if err := writeFile(certPath, cert, 0o644); err != nil {
		return nil, nil, false, fmt.Errorf("cannot write certificate: %v", err)
	}
	return cert, key, true, nil
}

// Create creates a self-signed RSA-2048 application instance certificate.
func Create(appURI, appName string) ([]byte, *rsa.PrivateKey, error) {
	if appURI == "" {
		return nil, nil, errors.New("applicationUri is required for the client certificate")
	}
	u, err := url.Parse(appURI)
	if err != nil {
		return nil, nil, fmt.Errorf("invalid applicationUri: %v", err)
	}
	key, err := rsa.GenerateKey(rand.Reader, KeyBits)
	if err != nil {
		return nil, nil, err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 127))
	if err != nil {
		return nil, nil, err
	}
	if appName == "" {
		appName = "JEVis OPC UA Bridge"
	}
	host, _ := os.Hostname()
	now := time.Now()
	tmpl := &x509.Certificate{
		SerialNumber: serial,
		Subject: pkix.Name{
			CommonName:   appName,
			Organization: []string{"JEVis"},
		},
		NotBefore: now.Add(-time.Hour),
		NotAfter:  now.Add(Validity),
		KeyUsage: x509.KeyUsageDigitalSignature | x509.KeyUsageContentCommitment |
			x509.KeyUsageKeyEncipherment | x509.KeyUsageDataEncipherment | x509.KeyUsageCertSign,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth},
		BasicConstraintsValid: true,
		IsCA:                  true, // self-signed, as Milo did; lets validators verify the signature
		MaxPathLenZero:        true,
		URIs:                  []*url.URL{u},
	}
	if host != "" {
		tmpl.DNSNames = []string{host}
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		return nil, nil, err
	}
	return der, key, nil
}

// CertificateURI returns the first URI of a DER certificate's subject
// alternative names.
func CertificateURI(der []byte) string {
	c, err := x509.ParseCertificate(der)
	if err != nil || len(c.URIs) == 0 {
		return ""
	}
	return c.URIs[0].String()
}

// Thumbprint returns the hex SHA-1 thumbprint of a DER certificate.
func Thumbprint(der []byte) string {
	sum := sha1.Sum(der)
	return hex.EncodeToString(sum[:])
}

// IsTrusted reports whether the server certificate is one of the
// certificates in dir/trusted (*.der, DER or PEM content).
func IsTrusted(dir string, serverCert []byte) (bool, error) {
	if len(serverCert) == 0 {
		return false, nil
	}
	entries, err := os.ReadDir(filepath.Join(dir, TrustedDir))
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return false, nil
		}
		return false, err
	}
	for _, e := range entries {
		if e.IsDir() || !strings.EqualFold(filepath.Ext(e.Name()), ".der") {
			continue
		}
		b, err := os.ReadFile(filepath.Join(dir, TrustedDir, e.Name()))
		if err != nil {
			continue
		}
		der, err := ParseCertificate(b)
		if err != nil {
			continue
		}
		if bytes.Equal(der, serverCert) {
			return true, nil
		}
	}
	return false, nil
}

// SaveRejected stores an untrusted server certificate in dir/rejected so that
// an administrator can move it to dir/trusted. It returns the file path.
func SaveRejected(dir string, serverCert []byte) (string, error) {
	rd := filepath.Join(dir, RejectedDir)
	if err := os.MkdirAll(rd, 0o700); err != nil {
		return "", err
	}
	p := filepath.Join(rd, Thumbprint(serverCert)+".der")
	return p, writeFile(p, serverCert, 0o644)
}

func exists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}

func writeFile(p string, b []byte, mode os.FileMode) error {
	tmp := p + ".tmp"
	if err := os.WriteFile(tmp, b, mode); err != nil {
		return err
	}
	if err := os.Chmod(tmp, mode); err != nil {
		return err
	}
	return os.Rename(tmp, p)
}
