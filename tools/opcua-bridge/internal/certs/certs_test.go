// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package certs

import (
	"bytes"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

const uri = "urn:jevis:test"

func TestLoadOrCreateAndReuse(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "opcua")
	cert, key, created, err := LoadOrCreate(dir, uri, "JEVis Test")
	if err != nil || !created {
		t.Fatalf("create: %v %v", created, err)
	}
	c, err := x509.ParseCertificate(cert)
	if err != nil {
		t.Fatal(err)
	}
	if len(c.URIs) != 1 || c.URIs[0].String() != uri || CertificateURI(cert) != uri {
		t.Fatalf("URIs %v", c.URIs)
	}
	if key.N.BitLen() != KeyBits || c.Subject.CommonName != "JEVis Test" {
		t.Fatalf("key %d bits, CN %s", key.N.BitLen(), c.Subject.CommonName)
	}
	if err := c.CheckSignatureFrom(c); err != nil {
		t.Fatalf("not self-signed: %v", err)
	}
	if runtime.GOOS != "windows" {
		st, _ := os.Stat(filepath.Join(dir, KeyFileName))
		if st.Mode().Perm() != 0o600 {
			t.Fatalf("key mode %v", st.Mode())
		}
	}
	kb, _ := os.ReadFile(filepath.Join(dir, KeyFileName))
	if blk, _ := pem.Decode(kb); blk == nil || blk.Type != "PRIVATE KEY" {
		t.Fatal("key is not PEM")
	}

	cert2, key2, created2, err := LoadOrCreate(dir, "urn:other", "Other")
	if err != nil || created2 {
		t.Fatalf("reuse: %v %v", created2, err)
	}
	if !bytes.Equal(cert, cert2) || !key.Equal(key2) {
		t.Fatal("certificate was not reused")
	}
}

func TestLoadFilesPEMAndDER(t *testing.T) {
	dir := t.TempDir()
	cert, key, err := Create(uri, "")
	if err != nil {
		t.Fatal(err)
	}
	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: cert})
	keyPKCS1 := pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(key)})
	keyPKCS8, _ := x509.MarshalPKCS8PrivateKey(key)
	files := map[string][]byte{"c.pem": certPEM, "c.der": cert, "k1.pem": keyPKCS1, "k8.der": keyPKCS8}
	for n, b := range files {
		if err := os.WriteFile(filepath.Join(dir, n), b, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	for _, pair := range [][2]string{{"c.pem", "k1.pem"}, {"c.der", "k8.der"}} {
		c, k, err := LoadFiles(filepath.Join(dir, pair[0]), filepath.Join(dir, pair[1]))
		if err != nil {
			t.Fatalf("%v: %v", pair, err)
		}
		if !bytes.Equal(c, cert) || !k.Equal(key) {
			t.Fatalf("%v: content differs", pair)
		}
	}
	if _, _, err := LoadFiles(filepath.Join(dir, "k1.pem"), filepath.Join(dir, "k1.pem")); err == nil {
		t.Fatal("key as certificate accepted")
	}
	if _, _, err := LoadFiles(filepath.Join(dir, "missing"), filepath.Join(dir, "k1.pem")); err == nil {
		t.Fatal("missing file accepted")
	}
}

func TestCreateNeedsURI(t *testing.T) {
	if _, _, err := Create("", "x"); err == nil {
		t.Fatal("empty URI accepted")
	}
}

func TestTrust(t *testing.T) {
	dir := t.TempDir()
	server, _, err := Create("urn:server", "server")
	if err != nil {
		t.Fatal(err)
	}
	other, _, _ := Create("urn:other", "other")
	if ok, err := IsTrusted(dir, server); ok || err != nil {
		t.Fatalf("no trust dir: %v %v", ok, err)
	}
	path, err := SaveRejected(dir, server)
	if err != nil || filepath.Base(path) != Thumbprint(server)+".der" {
		t.Fatalf("rejected: %s %v", path, err)
	}
	if err := os.MkdirAll(filepath.Join(dir, TrustedDir), 0o700); err != nil {
		t.Fatal(err)
	}
	_ = os.WriteFile(filepath.Join(dir, TrustedDir, "other.der"), other, 0o644)
	_ = os.WriteFile(filepath.Join(dir, TrustedDir, "junk.der"), []byte("junk"), 0o644)
	if ok, _ := IsTrusted(dir, server); ok {
		t.Fatal("untrusted certificate accepted")
	}
	// moving the rejected certificate to trusted makes it trusted
	if err := os.Rename(path, filepath.Join(dir, TrustedDir, "server.der")); err != nil {
		t.Fatal(err)
	}
	if ok, err := IsTrusted(dir, server); !ok || err != nil {
		t.Fatalf("trusted certificate rejected: %v", err)
	}
	if ok, _ := IsTrusted(dir, nil); ok {
		t.Fatal("empty certificate trusted")
	}
}
