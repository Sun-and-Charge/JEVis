// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

// Package endpoint describes OPC UA endpoints in protocol form and selects the
// endpoint to connect to.
package endpoint

import (
	"encoding/base64"
	"fmt"
	"strings"

	"github.com/gopcua/opcua/ua"
)

const policyPrefix = "http://opcfoundation.org/UA/SecurityPolicy#"

// Policies are the security policy short names supported by the bridge.
var Policies = []string{
	"None", "Basic128Rsa15", "Basic256", "Basic256Sha256", "Aes128_Sha256_RsaOaep", "Aes256_Sha256_RsaPss",
}

// Description is the protocol form of an endpoint.
type Description struct {
	URL               string   `json:"url"`
	SecurityPolicyURI string   `json:"securityPolicyUri"`
	SecurityPolicy    string   `json:"securityPolicy"`
	SecurityMode      string   `json:"securityMode"`
	SecurityLevel     int      `json:"securityLevel"`
	UserTokens        []string `json:"userTokens"`
	ServerCertificate *string  `json:"serverCertificate"`
}

// Describe converts an endpoint description to its protocol form.
func Describe(ep *ua.EndpointDescription) Description {
	d := Description{
		URL:               ep.EndpointURL,
		SecurityPolicyURI: ep.SecurityPolicyURI,
		SecurityPolicy:    PolicyName(ep.SecurityPolicyURI),
		SecurityMode:      ModeName(ep.SecurityMode),
		SecurityLevel:     int(ep.SecurityLevel),
		UserTokens:        []string{},
	}
	seen := map[string]bool{}
	for _, t := range ep.UserIdentityTokens {
		if t == nil {
			continue
		}
		n := TokenName(t.TokenType)
		if !seen[n] {
			seen[n] = true
			d.UserTokens = append(d.UserTokens, n)
		}
	}
	if len(ep.ServerCertificate) > 0 {
		s := base64.StdEncoding.EncodeToString(ep.ServerCertificate)
		d.ServerCertificate = &s
	}
	return d
}

// PolicyName returns the short name of a security policy URI; unknown URIs
// are returned unchanged.
func PolicyName(uri string) string {
	if strings.HasPrefix(uri, policyPrefix) {
		return strings.TrimPrefix(uri, policyPrefix)
	}
	return uri
}

// PolicyURI returns the URI of a security policy given as short name or URI.
// ok is false for an unsupported policy.
func PolicyURI(name string) (string, bool) {
	short := PolicyName(strings.TrimSpace(name))
	for _, p := range Policies {
		if strings.EqualFold(p, short) {
			return policyPrefix + p, true
		}
	}
	return "", false
}

// ModeName returns the name of a message security mode.
func ModeName(m ua.MessageSecurityMode) string {
	switch m {
	case ua.MessageSecurityModeNone:
		return "None"
	case ua.MessageSecurityModeSign:
		return "Sign"
	case ua.MessageSecurityModeSignAndEncrypt:
		return "SignAndEncrypt"
	}
	return "Invalid"
}

// ParseMode parses a message security mode name.
func ParseMode(s string) (ua.MessageSecurityMode, bool) {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "none":
		return ua.MessageSecurityModeNone, true
	case "sign":
		return ua.MessageSecurityModeSign, true
	case "signandencrypt":
		return ua.MessageSecurityModeSignAndEncrypt, true
	}
	return ua.MessageSecurityModeInvalid, false
}

// TokenName returns the name of a user token type.
func TokenName(t ua.UserTokenType) string {
	switch t {
	case ua.UserTokenTypeAnonymous:
		return "Anonymous"
	case ua.UserTokenTypeUserName:
		return "UserName"
	case ua.UserTokenTypeCertificate:
		return "Certificate"
	case ua.UserTokenTypeIssuedToken:
		return "IssuedToken"
	}
	return fmt.Sprintf("UserTokenType(%d)", t)
}

// Criteria select an endpoint. Empty or "auto" policy/mode match any.
type Criteria struct {
	Policy string // short name, URI, "auto" or ""
	Mode   string // None, Sign, SignAndEncrypt, "auto" or ""
	User   bool   // a user name is given: the endpoint must offer UserName tokens
}

// InvalidCriteriaError reports an unknown policy or mode name.
type InvalidCriteriaError struct{ Msg string }

func (e *InvalidCriteriaError) Error() string { return e.Msg }

// Select returns the endpoint with the highest security level that matches
// the criteria (like Milo's autoSelectEndpoint): binary TCP transport, a
// supported security policy, a user token type allowing the credentials
// (UserName if a user is given, else Anonymous) and the requested policy and
// mode unless "auto". Ties keep the server's order.
func Select(eps []*ua.EndpointDescription, c Criteria) (*ua.EndpointDescription, error) {
	wantPolicy := ""
	if p := strings.TrimSpace(c.Policy); p != "" && !strings.EqualFold(p, "auto") {
		uri, ok := PolicyURI(p)
		if !ok {
			return nil, &InvalidCriteriaError{fmt.Sprintf("unknown securityPolicy %q", c.Policy)}
		}
		wantPolicy = uri
	}
	wantMode := ua.MessageSecurityModeInvalid
	if m := strings.TrimSpace(c.Mode); m != "" && !strings.EqualFold(m, "auto") {
		mode, ok := ParseMode(m)
		if !ok {
			return nil, &InvalidCriteriaError{fmt.Sprintf("unknown securityMode %q", c.Mode)}
		}
		wantMode = mode
	}
	wantToken := ua.UserTokenTypeAnonymous
	if c.User {
		wantToken = ua.UserTokenTypeUserName
	}

	var best *ua.EndpointDescription
	for _, ep := range eps {
		if ep == nil || !binaryTransport(ep) {
			continue
		}
		if _, ok := PolicyURI(ep.SecurityPolicyURI); !ok || !strings.HasPrefix(ep.SecurityPolicyURI, policyPrefix) {
			continue
		}
		if wantPolicy != "" && ep.SecurityPolicyURI != wantPolicy {
			continue
		}
		if wantMode != ua.MessageSecurityModeInvalid && ep.SecurityMode != wantMode {
			continue
		}
		if ep.SecurityMode == ua.MessageSecurityModeInvalid {
			continue
		}
		if !hasToken(ep, wantToken) {
			continue
		}
		if best == nil || ep.SecurityLevel > best.SecurityLevel {
			best = ep
		}
	}
	if best == nil {
		policy, mode, token := "auto", "auto", TokenName(wantToken)
		if wantPolicy != "" {
			policy = PolicyName(wantPolicy)
		}
		if wantMode != ua.MessageSecurityModeInvalid {
			mode = ModeName(wantMode)
		}
		return nil, fmt.Errorf("no endpoint matches securityPolicy %s, securityMode %s, user token %s (%d endpoints offered)",
			policy, mode, token, len(eps))
	}
	return best, nil
}

func binaryTransport(ep *ua.EndpointDescription) bool {
	if ep.TransportProfileURI != "" && !strings.HasSuffix(ep.TransportProfileURI, "uatcp-uasc-uabinary") {
		return false
	}
	return ep.EndpointURL == "" || strings.HasPrefix(strings.ToLower(ep.EndpointURL), "opc.tcp://")
}

func hasToken(ep *ua.EndpointDescription, t ua.UserTokenType) bool {
	for _, p := range ep.UserIdentityTokens {
		if p != nil && p.TokenType == t {
			return true
		}
	}
	return false
}
