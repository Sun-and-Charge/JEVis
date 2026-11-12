// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package bridge

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/certs"
	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/convert"
	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/endpoint"
	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/protocol"
	"github.com/gopcua/opcua/ua"
)

// ---- endpoints ----

type endpointsParams struct {
	protocol.Common
	URL string `json:"url"`
}

func (b *Bridge) endpoints(req *protocol.Request) (any, *protocol.Error) {
	var p endpointsParams
	if err := req.Decode(&p); err != nil {
		return nil, err
	}
	if strings.TrimSpace(p.URL) == "" {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "endpoints: url is required")
	}
	ctx, cancel := b.context(p.Common)
	defer cancel()
	eps, err := b.Connector.Endpoints(ctx, p.URL)
	if err != nil {
		return nil, uaError(err, protocol.CodeConnectFailed)
	}
	out := make([]endpoint.Description, 0, len(eps))
	for _, ep := range eps {
		if ep != nil {
			out = append(out, endpoint.Describe(ep))
		}
	}
	return out, nil
}

// ---- connect ----

type connectParams struct {
	protocol.Common
	URL                    string  `json:"url"`
	SecurityPolicy         string  `json:"securityPolicy"`
	SecurityMode           string  `json:"securityMode"`
	User                   *string `json:"user"`
	Password               *string `json:"password"`
	CertFile               *string `json:"certFile"`
	KeyFile                *string `json:"keyFile"`
	CertDir                *string `json:"certDir"`
	ApplicationName        *string `json:"applicationName"`
	ApplicationURI         *string `json:"applicationUri"`
	TrustServerCertificate *bool   `json:"trustServerCertificate"`
}

type connectResult struct {
	Endpoint         endpoint.Description `json:"endpoint"`
	SessionTimeoutMs int64                `json:"sessionTimeoutMs"`
}

// Defaults of optional connect parameters.
const (
	DefaultApplicationName = "JEVis"
	DefaultApplicationURI  = "urn:jevis:opcua-bridge"
)

// DefaultCertDir is used when connect gives no certDir.
func DefaultCertDir() string {
	if h, err := os.UserHomeDir(); err == nil && h != "" {
		return filepath.Join(h, ".jevis", "opcua")
	}
	return "opcua"
}

func str(p *string, def string) string {
	if p == nil || strings.TrimSpace(*p) == "" {
		return def
	}
	return *p
}

func (b *Bridge) connect(req *protocol.Request) (any, *protocol.Error) {
	var p connectParams
	if err := req.Decode(&p); err != nil {
		return nil, err
	}
	if strings.TrimSpace(p.URL) == "" {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "connect: url is required")
	}
	b.closeSession()
	if p.TimeoutMs != nil && *p.TimeoutMs > 0 {
		b.timeout = time.Duration(*p.TimeoutMs) * time.Millisecond
	} else {
		b.timeout = DefaultTimeout
	}
	cfg := ConnectConfig{
		User:            str(p.User, ""),
		Password:        str(p.Password, ""),
		ApplicationName: str(p.ApplicationName, DefaultApplicationName),
		ApplicationURI:  str(p.ApplicationURI, DefaultApplicationURI),
		Timeout:         b.timeout,
	}
	if p.Password != nil {
		cfg.Password = *p.Password // passwords may contain blanks
	}
	certDir := str(p.CertDir, DefaultCertDir())
	trust := p.TrustServerCertificate == nil || *p.TrustServerCertificate

	ctx, cancel := context.WithTimeout(context.Background(), b.timeout)
	defer cancel()

	eps, err := b.Connector.Endpoints(ctx, p.URL)
	if err != nil {
		return nil, uaError(err, protocol.CodeConnectFailed)
	}
	ep, err := endpoint.Select(eps, endpoint.Criteria{
		Policy: p.SecurityPolicy, Mode: p.SecurityMode, User: cfg.User != "",
	})
	if err != nil {
		if _, ok := err.(*endpoint.InvalidCriteriaError); ok {
			return nil, protocol.Errorf(protocol.CodeBadRequest, "connect: %v", err)
		}
		return nil, protocol.Errorf(protocol.CodeConnectFailed, "%v", err)
	}
	desc := endpoint.Describe(ep)
	b.Log.Printf("connecting to %s, endpoint %s/%s, user token %s", p.URL, desc.SecurityPolicy, desc.SecurityMode,
		map[bool]string{true: "UserName", false: "Anonymous"}[cfg.User != ""])

	if ep.SecurityPolicyURI != ua.SecurityPolicyURINone {
		if perr := b.clientCertificate(&cfg, p, certDir); perr != nil {
			return nil, perr
		}
		if !trust {
			ok, err := certs.IsTrusted(certDir, ep.ServerCertificate)
			if err != nil {
				return nil, protocol.Errorf(protocol.CodeConnectFailed, "cannot read trusted certificates: %v", err)
			}
			if !ok {
				msg := "server certificate is not trusted (not in " + filepath.Join(certDir, certs.TrustedDir) + ")"
				if len(ep.ServerCertificate) > 0 {
					if path, err := certs.SaveRejected(certDir, ep.ServerCertificate); err == nil {
						msg += "; saved as " + path
					}
				}
				return nil, protocol.WithStatus(protocol.CodeConnectFailed, msg, convert.StatusName(ua.StatusBadCertificateUntrusted))
			}
		}
	}

	client, err := b.Connector.Connect(ctx, p.URL, ep, cfg)
	if err != nil {
		return nil, uaError(err, protocol.CodeConnectFailed)
	}
	b.client = client
	b.Log.Printf("connected, session timeout %v", client.SessionTimeout())
	return connectResult{Endpoint: desc, SessionTimeoutMs: client.SessionTimeout().Milliseconds()}, nil
}

func (b *Bridge) clientCertificate(cfg *ConnectConfig, p connectParams, certDir string) *protocol.Error {
	certFile, keyFile := str(p.CertFile, ""), str(p.KeyFile, "")
	if certFile != "" || keyFile != "" {
		if certFile == "" || keyFile == "" {
			return protocol.Errorf(protocol.CodeBadRequest, "connect: certFile and keyFile must be given together")
		}
		cert, key, err := certs.LoadFiles(certFile, keyFile)
		if err != nil {
			return protocol.Errorf(protocol.CodeConnectFailed, "client certificate: %v", err)
		}
		cfg.Certificate, cfg.PrivateKey = cert, key
		return nil
	}
	cert, key, created, err := certs.LoadOrCreate(certDir, cfg.ApplicationURI, cfg.ApplicationName)
	if err != nil {
		return protocol.Errorf(protocol.CodeConnectFailed, "client certificate: %v", err)
	}
	if created {
		b.Log.Printf("created client certificate %s", filepath.Join(certDir, certs.CertFileName))
	} else if uri := certs.CertificateURI(cert); uri != cfg.ApplicationURI {
		b.Log.Printf("client certificate has application URI %s (requested %s); using the certificate's", uri, cfg.ApplicationURI)
	}
	cfg.Certificate, cfg.PrivateKey = cert, key
	return nil
}

// ---- browse ----

type browseParams struct {
	protocol.Common
	NodeID      string   `json:"nodeId"`
	NodeClasses []string `json:"nodeClasses"`
	References  string   `json:"references"`
}

type browseEntry struct {
	NodeID         string  `json:"nodeId"`
	BrowseName     string  `json:"browseName"`
	DisplayName    string  `json:"displayName"`
	NodeClass      string  `json:"nodeClass"`
	TypeDefinition *string `json:"typeDefinition"`
	ReferenceType  string  `json:"referenceType"`
}

// maxBrowseNext limits the BrowseNext round trips (protection against servers
// returning continuation points forever).
const maxBrowseNext = 10000

func (b *Bridge) browse(req *protocol.Request) (any, *protocol.Error) {
	var p browseParams
	if err := req.Decode(&p); err != nil {
		return nil, err
	}
	desc, perr := browseDescription(p)
	if perr != nil {
		return nil, perr
	}
	c, perr := b.session()
	if perr != nil {
		return nil, perr
	}
	ctx, cancel := b.context(p.Common)
	defer cancel()

	res, err := c.Browse(ctx, &ua.BrowseRequest{NodesToBrowse: []*ua.BrowseDescription{desc}})
	if err != nil {
		return nil, uaError(err, protocol.CodeInternal)
	}
	if len(res.Results) != 1 {
		return nil, protocol.Errorf(protocol.CodeOPCUA, "browse: %d results for 1 node", len(res.Results))
	}
	r := res.Results[0]
	out := []browseEntry{}
	for i := 0; ; i++ {
		if convert.IsBad(r.StatusCode) {
			return nil, statusError("browse "+p.NodeID, r.StatusCode)
		}
		for _, ref := range r.References {
			out = append(out, browseEntryOf(ref))
		}
		if len(r.ContinuationPoint) == 0 {
			break
		}
		if i >= maxBrowseNext {
			return nil, protocol.Errorf(protocol.CodeOPCUA, "browse: too many continuation points")
		}
		next, err := c.BrowseNext(ctx, &ua.BrowseNextRequest{ContinuationPoints: [][]byte{r.ContinuationPoint}})
		if err != nil {
			return nil, uaError(err, protocol.CodeInternal)
		}
		if len(next.Results) != 1 {
			return nil, protocol.Errorf(protocol.CodeOPCUA, "browse next: %d results for 1 node", len(next.Results))
		}
		r = next.Results[0]
	}
	return out, nil
}

func browseDescription(p browseParams) (*ua.BrowseDescription, *protocol.Error) {
	n, err := convert.ParseNodeID(str(&p.NodeID, "i=85"))
	if err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "browse: %v", err)
	}
	var mask uint32
	for _, name := range p.NodeClasses {
		c, ok := convert.NodeClassFromName(name)
		if !ok {
			return nil, protocol.Errorf(protocol.CodeBadRequest, "browse: unknown node class %q", name)
		}
		mask |= uint32(c)
	}
	refType := uint32(33) // HierarchicalReferences
	switch strings.ToLower(strings.TrimSpace(p.References)) {
	case "", "hierarchical":
	case "all":
		refType = 31 // References
	default:
		return nil, protocol.Errorf(protocol.CodeBadRequest, "browse: references must be Hierarchical or All")
	}
	return &ua.BrowseDescription{
		NodeID:          n,
		BrowseDirection: ua.BrowseDirectionForward,
		ReferenceTypeID: ua.NewNumericNodeID(0, refType),
		IncludeSubtypes: true,
		NodeClassMask:   mask,
		ResultMask:      uint32(ua.BrowseResultMaskAll),
	}, nil
}

func browseEntryOf(ref *ua.ReferenceDescription) browseEntry {
	e := browseEntry{
		NodeID:        convert.ExpandedNodeIDString(ref.NodeID),
		BrowseName:    convert.QualifiedNameString(ref.BrowseName),
		DisplayName:   convert.LocalizedTextString(ref.DisplayName),
		NodeClass:     convert.NodeClassName(ref.NodeClass),
		ReferenceType: convert.NodeIDString(ref.ReferenceTypeID),
	}
	if ref.TypeDefinition != nil && !convert.IsNullNodeID(ref.TypeDefinition.NodeID) {
		s := convert.ExpandedNodeIDString(ref.TypeDefinition)
		e.TypeDefinition = &s
	}
	return e
}

// ---- read ----

type readParams struct {
	protocol.Common
	NodeIDs   []string `json:"nodeIds"`
	Attribute string   `json:"attribute"`
}

type readEntry struct {
	NodeID          string `json:"nodeId"`
	Value           any    `json:"value"`
	DataType        any    `json:"dataType"`
	SourceTimestamp any    `json:"sourceTimestamp"`
	ServerTimestamp any    `json:"serverTimestamp"`
	Status          string `json:"status"`
	StatusCode      uint32 `json:"statusCode"`
}

var readAttributes = map[string]ua.AttributeID{
	"value":       ua.AttributeIDValue,
	"datatype":    ua.AttributeIDDataType,
	"displayname": ua.AttributeIDDisplayName,
	"browsename":  ua.AttributeIDBrowseName,
	"description": ua.AttributeIDDescription,
	"nodeclass":   ua.AttributeIDNodeClass,
	"accesslevel": ua.AttributeIDAccessLevel,
	"historizing": ua.AttributeIDHistorizing,
}

func (b *Bridge) read(req *protocol.Request) (any, *protocol.Error) {
	var p readParams
	if err := req.Decode(&p); err != nil {
		return nil, err
	}
	attr, ok := readAttributes[strings.ToLower(str(&p.Attribute, "Value"))]
	if !ok {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "read: unknown attribute %q", p.Attribute)
	}
	if len(p.NodeIDs) == 0 {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "read: nodeIds is required")
	}
	ids := make([]*ua.ReadValueID, len(p.NodeIDs))
	for i, s := range p.NodeIDs {
		n, err := convert.ParseNodeID(s)
		if err != nil {
			return nil, protocol.Errorf(protocol.CodeBadRequest, "read: %v", err)
		}
		ids[i] = &ua.ReadValueID{NodeID: n, AttributeID: attr}
	}
	c, perr := b.session()
	if perr != nil {
		return nil, perr
	}
	ctx, cancel := b.context(p.Common)
	defer cancel()
	res, err := c.Read(ctx, &ua.ReadRequest{NodesToRead: ids, TimestampsToReturn: ua.TimestampsToReturnBoth})
	if err != nil {
		return nil, uaError(err, protocol.CodeInternal)
	}
	if len(res.Results) != len(ids) {
		return nil, protocol.Errorf(protocol.CodeOPCUA, "read: %d results for %d nodes", len(res.Results), len(ids))
	}
	out := make([]readEntry, len(ids))
	for i, dv := range res.Results {
		out[i] = readEntryOf(p.NodeIDs[i], attr, dv)
	}
	return out, nil
}

func readEntryOf(nodeID string, attr ua.AttributeID, dv *ua.DataValue) readEntry {
	e := readEntry{NodeID: nodeID}
	if dv == nil {
		e.Status = convert.StatusName(ua.StatusBadNoData)
		e.StatusCode = uint32(ua.StatusBadNoData)
		return e
	}
	e.Status, e.StatusCode = convert.StatusName(dv.Status), uint32(dv.Status)
	e.SourceTimestamp = convert.FormatTimePtr(dv.SourceTimestamp)
	e.ServerTimestamp = convert.FormatTimePtr(dv.ServerTimestamp)
	if dv.Value == nil || dv.Value.Type() == ua.TypeIDNull {
		return e
	}
	if t := convert.VariantType(dv.Value); t != "" {
		e.DataType = t
	}
	switch attr {
	case ua.AttributeIDDataType:
		if n, ok := dv.Value.Value().(*ua.NodeID); ok {
			e.Value = convert.DataTypeName(n)
			return e
		}
	case ua.AttributeIDNodeClass:
		e.Value = convert.NodeClassName(ua.NodeClass(dv.Value.Int()))
		return e
	}
	e.Value = convert.VariantValue(dv.Value)
	return e
}

// ---- write ----

type writeParams struct {
	protocol.Common
	NodeID   string `json:"nodeId"`
	Value    any    `json:"value"`
	DataType string `json:"dataType"`
}

type statusResult struct {
	Status     string `json:"status"`
	StatusCode uint32 `json:"statusCode"`
}

func (b *Bridge) write(req *protocol.Request) (any, *protocol.Error) {
	var p writeParams
	if err := req.Decode(&p); err != nil {
		return nil, err
	}
	n, err := convert.ParseNodeID(p.NodeID)
	if err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "write: %v", err)
	}
	dtName := str(&p.DataType, "auto")
	var typ ua.TypeID
	if !strings.EqualFold(dtName, "auto") {
		t, ok := convert.WritableType(dtName)
		if !ok {
			return nil, protocol.Errorf(protocol.CodeBadRequest, "write: unsupported dataType %q", p.DataType)
		}
		typ = t
	}
	if p.Value == nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "write: value is required")
	}
	c, perr := b.session()
	if perr != nil {
		return nil, perr
	}
	ctx, cancel := b.context(p.Common)
	defer cancel()

	if typ == 0 {
		t, perr := b.nodeDataType(ctx, c, n)
		if perr != nil {
			return nil, perr
		}
		typ = t
	}
	v, err := convert.WriteValue(p.Value, typ)
	if err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "write: %v", err)
	}
	variant, err := ua.NewVariant(v)
	if err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "write: %v", err)
	}
	res, err := c.Write(ctx, &ua.WriteRequest{NodesToWrite: []*ua.WriteValue{{
		NodeID:      n,
		AttributeID: ua.AttributeIDValue,
		Value:       &ua.DataValue{EncodingMask: ua.DataValueValue, Value: variant},
	}}})
	if err != nil {
		return nil, uaError(err, protocol.CodeInternal)
	}
	if len(res.Results) != 1 {
		return nil, protocol.Errorf(protocol.CodeOPCUA, "write: %d results for 1 node", len(res.Results))
	}
	sc := res.Results[0]
	if convert.IsBad(sc) {
		return nil, statusError("write "+p.NodeID, sc)
	}
	return statusResult{Status: convert.StatusName(sc), StatusCode: uint32(sc)}, nil
}

// nodeDataType reads the DataType attribute and maps it to a built-in type.
func (b *Bridge) nodeDataType(ctx context.Context, c Client, n *ua.NodeID) (ua.TypeID, *protocol.Error) {
	res, err := c.Read(ctx, &ua.ReadRequest{NodesToRead: []*ua.ReadValueID{{NodeID: n, AttributeID: ua.AttributeIDDataType}}})
	if err != nil {
		return 0, uaError(err, protocol.CodeInternal)
	}
	if len(res.Results) != 1 || res.Results[0] == nil {
		return 0, protocol.Errorf(protocol.CodeOPCUA, "write: cannot read the data type of %s", n)
	}
	dv := res.Results[0]
	if convert.IsBad(dv.Status) {
		return 0, statusError("write: read data type of "+n.String(), dv.Status)
	}
	var dt *ua.NodeID
	if dv.Value != nil {
		dt, _ = dv.Value.Value().(*ua.NodeID)
	}
	t, ok := convert.WritableTypeOfDataType(dt)
	if !ok {
		return 0, protocol.Errorf(protocol.CodeBadRequest, "write: data type %s of %s cannot be written, give dataType explicitly",
			convert.DataTypeName(dt), n)
	}
	return t, nil
}
