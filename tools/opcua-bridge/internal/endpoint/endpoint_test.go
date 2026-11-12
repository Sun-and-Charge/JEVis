// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package endpoint

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/gopcua/opcua/ua"
)

const binary = "http://opcfoundation.org/UA-Profile/Transport/uatcp-uasc-uabinary"

func ep(policy string, mode ua.MessageSecurityMode, level uint8, tokens ...ua.UserTokenType) *ua.EndpointDescription {
	e := &ua.EndpointDescription{
		EndpointURL:         "opc.tcp://host:4840",
		SecurityPolicyURI:   policyPrefix + policy,
		SecurityMode:        mode,
		SecurityLevel:       level,
		TransportProfileURI: binary,
	}
	for _, t := range tokens {
		e.UserIdentityTokens = append(e.UserIdentityTokens, &ua.UserTokenPolicy{TokenType: t})
	}
	return e
}

var (
	anon = ua.UserTokenTypeAnonymous
	user = ua.UserTokenTypeUserName
	none = ua.MessageSecurityModeNone
	sign = ua.MessageSecurityModeSign
	enc  = ua.MessageSecurityModeSignAndEncrypt
)

func sample() []*ua.EndpointDescription {
	return []*ua.EndpointDescription{
		ep("None", none, 0, anon, user),
		ep("Basic256Sha256", sign, 5, user),
		ep("Basic256Sha256", enc, 10, user),
		ep("Basic128Rsa15", enc, 3, anon, user),
		ep("Aes128_Sha256_RsaOaep", sign, 4, anon),
	}
}

func describe(e *ua.EndpointDescription) string {
	return PolicyName(e.SecurityPolicyURI) + "/" + ModeName(e.SecurityMode)
}

func TestSelectAuto(t *testing.T) {
	cases := []struct {
		c    Criteria
		want string
	}{
		{Criteria{Policy: "auto", Mode: "auto", User: true}, "Basic256Sha256/SignAndEncrypt"},
		{Criteria{Policy: "auto", Mode: "auto"}, "Aes128_Sha256_RsaOaep/Sign"},
		{Criteria{}, "Aes128_Sha256_RsaOaep/Sign"},
		{Criteria{Policy: "None", Mode: "auto"}, "None/None"},
		{Criteria{Policy: "auto", Mode: "SignAndEncrypt"}, "Basic128Rsa15/SignAndEncrypt"},
		{Criteria{Policy: "Basic256Sha256", Mode: "auto", User: true}, "Basic256Sha256/SignAndEncrypt"},
		{Criteria{Policy: "basic256sha256", Mode: "sign", User: true}, "Basic256Sha256/Sign"},
		{Criteria{Policy: policyPrefix + "None", Mode: "None", User: true}, "None/None"},
	}
	for _, c := range cases {
		got, err := Select(sample(), c.c)
		if err != nil {
			t.Errorf("%+v: %v", c.c, err)
			continue
		}
		if describe(got) != c.want {
			t.Errorf("%+v: got %s want %s", c.c, describe(got), c.want)
		}
	}
}

func TestSelectNoMatch(t *testing.T) {
	_, err := Select(sample(), Criteria{Policy: "Basic256Sha256", Mode: "auto"})
	if err == nil || !strings.Contains(err.Error(), "no endpoint") {
		t.Fatalf("anonymous on Basic256Sha256: %v", err)
	}
	if _, err := Select(nil, Criteria{}); err == nil {
		t.Fatal("empty list")
	}
	_, err = Select(sample(), Criteria{Policy: "Basic512"})
	if _, ok := err.(*InvalidCriteriaError); !ok {
		t.Fatalf("unknown policy: %v", err)
	}
	_, err = Select(sample(), Criteria{Mode: "Encrypt"})
	if _, ok := err.(*InvalidCriteriaError); !ok {
		t.Fatalf("unknown mode: %v", err)
	}
}

func TestSelectSkipsUnsupported(t *testing.T) {
	https := ep("Basic256Sha256", enc, 200, anon)
	https.EndpointURL = "https://host:443"
	https.TransportProfileURI = "http://opcfoundation.org/UA-Profile/Transport/https-uabinary"
	unknown := ep("Basic256Sha256", enc, 100, anon)
	unknown.SecurityPolicyURI = "http://example.com/Policy#Strange"
	eps := []*ua.EndpointDescription{https, unknown, ep("None", none, 0, anon)}
	got, err := Select(eps, Criteria{})
	if err != nil || describe(got) != "None/None" {
		t.Fatalf("got %v %v", got, err)
	}
}

func TestSelectTieKeepsServerOrder(t *testing.T) {
	a := ep("Basic256Sha256", enc, 0, anon)
	b := ep("Basic256", enc, 0, anon)
	got, _ := Select([]*ua.EndpointDescription{a, b}, Criteria{})
	if got != a {
		t.Fatal("first endpoint expected")
	}
}

func TestDescribe(t *testing.T) {
	e := ep("Basic256Sha256", enc, 10, anon, user, user)
	e.ServerCertificate = []byte{1, 2, 3}
	b, _ := json.Marshal(Describe(e))
	want := `{"url":"opc.tcp://host:4840","securityPolicyUri":"http://opcfoundation.org/UA/SecurityPolicy#Basic256Sha256",` +
		`"securityPolicy":"Basic256Sha256","securityMode":"SignAndEncrypt","securityLevel":10,` +
		`"userTokens":["Anonymous","UserName"],"serverCertificate":"AQID"}`
	if string(b) != want {
		t.Fatalf("got  %s\nwant %s", b, want)
	}
	b, _ = json.Marshal(Describe(ep("None", none, 0)))
	if !strings.Contains(string(b), `"userTokens":[],"serverCertificate":null`) {
		t.Fatal(string(b))
	}
}

func TestPolicyNames(t *testing.T) {
	for _, p := range Policies {
		uri, ok := PolicyURI(p)
		if !ok || PolicyName(uri) != p {
			t.Errorf("%s", p)
		}
		// all supported by gopcua
		if ua.FormatSecurityPolicyURI(p) != uri {
			t.Errorf("gopcua does not know %s", p)
		}
	}
}
