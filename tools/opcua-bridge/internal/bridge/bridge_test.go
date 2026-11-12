// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package bridge

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/certs"
	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/protocol"
	"github.com/gopcua/opcua/ua"
)

// ---- fakes ----

const policyURI = "http://opcfoundation.org/UA/SecurityPolicy#"

type fakeConnector struct {
	eps       []*ua.EndpointDescription
	client    *fakeClient
	connected *ua.EndpointDescription
	cfg       ConnectConfig
	err       error
}

func (f *fakeConnector) Endpoints(ctx context.Context, url string) ([]*ua.EndpointDescription, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.eps, nil
}

func (f *fakeConnector) Connect(ctx context.Context, url string, ep *ua.EndpointDescription, cfg ConnectConfig) (Client, error) {
	f.connected, f.cfg = ep, cfg
	if cfg.User == "demo" && cfg.Password != "secret" {
		return nil, ua.StatusBadUserAccessDenied
	}
	return f.client, nil
}

type fakeClient struct {
	values     map[string]*ua.DataValue // nodeId -> value
	dataTypes  map[string]*ua.NodeID
	refs       []*ua.ReferenceDescription
	pageSize   int
	history    []*ua.DataValue
	historyErr ua.StatusCode
	writes     []*ua.WriteValue
	writeRes   ua.StatusCode
	histReqs   []*ua.HistoryReadRequest
	browseReqs []*ua.BrowseRequest
	closed     bool
}

func (f *fakeClient) Read(ctx context.Context, req *ua.ReadRequest) (*ua.ReadResponse, error) {
	res := &ua.ReadResponse{}
	for _, r := range req.NodesToRead {
		id := r.NodeID.String()
		switch r.AttributeID {
		case ua.AttributeIDValue:
			if dv, ok := f.values[id]; ok {
				res.Results = append(res.Results, dv)
			} else {
				res.Results = append(res.Results, &ua.DataValue{Status: ua.StatusBadNodeIDUnknown})
			}
		case ua.AttributeIDDataType:
			if dt, ok := f.dataTypes[id]; ok {
				res.Results = append(res.Results, &ua.DataValue{Value: ua.MustVariant(dt)})
			} else {
				res.Results = append(res.Results, &ua.DataValue{Status: ua.StatusBadAttributeIDInvalid})
			}
		case ua.AttributeIDNodeClass:
			res.Results = append(res.Results, &ua.DataValue{Value: ua.MustVariant(int32(ua.NodeClassVariable))})
		default:
			res.Results = append(res.Results, &ua.DataValue{Value: ua.MustVariant(&ua.LocalizedText{Text: "Temp"})})
		}
	}
	return res, nil
}

func (f *fakeClient) Write(ctx context.Context, req *ua.WriteRequest) (*ua.WriteResponse, error) {
	f.writes = append(f.writes, req.NodesToWrite...)
	return &ua.WriteResponse{Results: []ua.StatusCode{f.writeRes}}, nil
}

func (f *fakeClient) page(start int) *ua.BrowseResult {
	end := start + f.pageSize
	r := &ua.BrowseResult{}
	if end < len(f.refs) {
		r.ContinuationPoint = []byte(fmt.Sprint(end))
	} else {
		end = len(f.refs)
	}
	r.References = f.refs[start:end]
	return r
}

func (f *fakeClient) Browse(ctx context.Context, req *ua.BrowseRequest) (*ua.BrowseResponse, error) {
	f.browseReqs = append(f.browseReqs, req)
	if req.NodesToBrowse[0].NodeID.String() == "ns=9;s=missing" {
		return &ua.BrowseResponse{Results: []*ua.BrowseResult{{StatusCode: ua.StatusBadNodeIDUnknown}}}, nil
	}
	return &ua.BrowseResponse{Results: []*ua.BrowseResult{f.page(0)}}, nil
}

func (f *fakeClient) BrowseNext(ctx context.Context, req *ua.BrowseNextRequest) (*ua.BrowseNextResponse, error) {
	var start int
	fmt.Sscan(string(req.ContinuationPoints[0]), &start)
	return &ua.BrowseNextResponse{Results: []*ua.BrowseResult{f.page(start)}}, nil
}

// HistoryRead serves f.history in pages of NumValuesPerNode (raw) or 3
// (processed); the continuation point is the next index.
func (f *fakeClient) HistoryRead(ctx context.Context, req *ua.HistoryReadRequest) (*ua.HistoryReadResponse, error) {
	f.histReqs = append(f.histReqs, req)
	if req.ReleaseContinuationPoints {
		return &ua.HistoryReadResponse{Results: []*ua.HistoryReadResult{{}}}, nil
	}
	if f.historyErr != 0 {
		return &ua.HistoryReadResponse{Results: []*ua.HistoryReadResult{{StatusCode: f.historyErr}}}, nil
	}
	n := 3
	if d, ok := req.HistoryReadDetails.Value.(*ua.ReadRawModifiedDetails); ok && d.NumValuesPerNode > 0 {
		n = int(d.NumValuesPerNode)
	}
	start := 0
	if cp := req.NodesToRead[0].ContinuationPoint; len(cp) > 0 {
		fmt.Sscan(string(cp), &start)
	}
	end := start + n
	r := &ua.HistoryReadResult{}
	if end < len(f.history) {
		r.ContinuationPoint = []byte(fmt.Sprint(end))
	} else {
		end = len(f.history)
	}
	r.HistoryData = &ua.ExtensionObject{Value: &ua.HistoryData{DataValues: f.history[start:end]}}
	return &ua.HistoryReadResponse{Results: []*ua.HistoryReadResult{r}}, nil
}

func (f *fakeClient) SessionTimeout() time.Duration { return 1200 * time.Second }

func (f *fakeClient) Close(ctx context.Context) error {
	f.closed = true
	return nil
}

// ---- helpers ----

var t0 = time.Date(2026, 11, 12, 8, 0, 0, 0, time.UTC)

func newFake() (*fakeConnector, *fakeClient) {
	cl := &fakeClient{
		values: map[string]*ua.DataValue{
			"ns=2;s=Temp": {Value: ua.MustVariant(21.5), SourceTimestamp: t0, ServerTimestamp: t0.Add(120 * time.Millisecond)},
			"ns=2;s=Bad":  {Status: ua.StatusBadNoCommunication},
		},
		dataTypes: map[string]*ua.NodeID{
			"ns=2;s=Temp":     ua.NewNumericNodeID(0, 11),
			"ns=2;s=Count":    ua.NewNumericNodeID(0, 6),
			"ns=2;s=Struct":   ua.NewNumericNodeID(3, 3001),
			"ns=2;s=Duration": ua.NewNumericNodeID(0, 290),
		},
		pageSize: 2,
	}
	for i := 0; i < 5; i++ {
		cl.refs = append(cl.refs, &ua.ReferenceDescription{
			ReferenceTypeID: ua.NewNumericNodeID(0, 35),
			IsForward:       true,
			NodeID:          ua.NewExpandedNodeID(ua.NewStringNodeID(2, fmt.Sprintf("Device%d", i)), "", 0),
			BrowseName:      &ua.QualifiedName{NamespaceIndex: 2, Name: fmt.Sprintf("Device%d", i)},
			DisplayName:     &ua.LocalizedText{Text: fmt.Sprintf("Device %d", i)},
			NodeClass:       ua.NodeClassObject,
			TypeDefinition:  ua.NewExpandedNodeID(ua.NewNumericNodeID(0, 58), "", 0),
		})
	}
	for i := 0; i < 10; i++ {
		cl.history = append(cl.history, &ua.DataValue{
			Value:           ua.MustVariant(float64(i)),
			SourceTimestamp: t0.Add(time.Duration(i) * 15 * time.Minute),
			ServerTimestamp: t0.Add(time.Duration(i)*15*time.Minute + time.Second),
		})
	}
	con := &fakeConnector{client: cl, eps: []*ua.EndpointDescription{
		{EndpointURL: "opc.tcp://fake:4840", SecurityPolicyURI: policyURI + "None", SecurityMode: ua.MessageSecurityModeNone,
			UserIdentityTokens: []*ua.UserTokenPolicy{{TokenType: ua.UserTokenTypeAnonymous}, {TokenType: ua.UserTokenTypeUserName}}},
		{EndpointURL: "opc.tcp://fake:4840", SecurityPolicyURI: policyURI + "Basic256Sha256", SecurityMode: ua.MessageSecurityModeSignAndEncrypt,
			SecurityLevel: 10, ServerCertificate: serverCert,
			UserIdentityTokens: []*ua.UserTokenPolicy{{TokenType: ua.UserTokenTypeUserName}}},
	}}
	return con, cl
}

var serverCert = func() []byte {
	c, _, err := certs.Create("urn:fake:server", "fake server")
	if err != nil {
		panic(err)
	}
	return c
}()

type response struct {
	ID     json.RawMessage `json:"id"`
	OK     bool            `json:"ok"`
	Result json.RawMessage `json:"result"`
	Error  *struct {
		Code    string  `json:"code"`
		Message string  `json:"message"`
		Status  *string `json:"status"`
	} `json:"error"`
}

func call(t *testing.T, b *Bridge, line string) response {
	t.Helper()
	resp, _ := b.Handle([]byte(line))
	raw, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("%s: marshal: %v", line, err)
	}
	var r response
	if err := json.Unmarshal(raw, &r); err != nil {
		t.Fatal(err)
	}
	return r
}

func ok(t *testing.T, b *Bridge, line string, result any) {
	t.Helper()
	r := call(t, b, line)
	if !r.OK {
		t.Fatalf("%s: error %+v", line, r.Error)
	}
	if result != nil {
		if err := json.Unmarshal(r.Result, result); err != nil {
			t.Fatalf("%s: result %s: %v", line, r.Result, err)
		}
	}
}

func fails(t *testing.T, b *Bridge, line, code, status string) response {
	t.Helper()
	r := call(t, b, line)
	if r.OK {
		t.Fatalf("%s: expected error %s, got %s", line, code, r.Result)
	}
	if r.Error.Code != code {
		t.Fatalf("%s: code %s (%s), want %s", line, r.Error.Code, r.Error.Message, code)
	}
	got := ""
	if r.Error.Status != nil {
		got = *r.Error.Status
	}
	if got != status {
		t.Fatalf("%s: status %q, want %q", line, got, status)
	}
	return r
}

func connected(t *testing.T) (*Bridge, *fakeConnector, *fakeClient) {
	con, cl := newFake()
	b := New(con, nil)
	ok(t, b, `{"id":1,"cmd":"connect","url":"opc.tcp://fake:4840","securityPolicy":"None","securityMode":"auto"}`, nil)
	return b, con, cl
}

// ---- tests ----

func TestRequestErrors(t *testing.T) {
	con, _ := newFake()
	b := New(con, nil)
	fails(t, b, `{"id":1,"cmd":"fly"}`, "bad_request", "")
	fails(t, b, `{"id":2,"cmd":"read","nodeIds":["i=84"],"colour":"red"}`, "bad_request", "")
	fails(t, b, `garbage`, "bad_request", "")
	fails(t, b, `{"id":3,"cmd":"read","nodeIds":["i=84"]}`, "not_connected", "")
	fails(t, b, `{"id":4,"cmd":"browse","nodeId":"i=85"}`, "not_connected", "")
	fails(t, b, `{"id":5,"cmd":"history-raw","nodeId":"i=85","from":"2026-11-01T00:00:00Z","to":"2026-11-02T00:00:00Z"}`, "not_connected", "")
	fails(t, b, `{"id":6,"cmd":"read","nodeIds":["bogus"]}`, "bad_request", "")
	fails(t, b, `{"id":7,"cmd":"connect"}`, "bad_request", "")
	r := call(t, b, `{"id":"abc","cmd":"nope"}`)
	if string(r.ID) != `"abc"` {
		t.Fatalf("id %s", r.ID)
	}
}

func TestEndpoints(t *testing.T) {
	con, _ := newFake()
	b := New(con, nil)
	var eps []map[string]any
	ok(t, b, `{"id":1,"cmd":"endpoints","url":"opc.tcp://fake:4840"}`, &eps)
	if len(eps) != 2 || eps[0]["securityPolicy"] != "None" || eps[1]["securityMode"] != "SignAndEncrypt" ||
		eps[0]["serverCertificate"] != nil || eps[1]["serverCertificate"] == nil {
		t.Fatalf("%v", eps)
	}
	con.err = fmt.Errorf("dial tcp: connection refused")
	fails(t, b, `{"id":2,"cmd":"endpoints","url":"opc.tcp://fake:4840"}`, "connect_failed", "")
	con.err = context.DeadlineExceeded
	fails(t, b, `{"id":3,"cmd":"endpoints","url":"opc.tcp://fake:4840"}`, "timeout", "")
}

func TestConnectAutoAndCertificate(t *testing.T) {
	con, _ := newFake()
	var logBuf bytes.Buffer
	b := New(con, log.New(&logBuf, "", 0))
	dir := filepath.Join(t.TempDir(), "certs")
	var res struct {
		Endpoint         map[string]any `json:"endpoint"`
		SessionTimeoutMs int64          `json:"sessionTimeoutMs"`
	}
	// anonymous: only the None endpoint allows it
	ok(t, b, `{"id":1,"cmd":"connect","url":"opc.tcp://fake:4840","securityPolicy":"auto","securityMode":"auto","certDir":`+jsonStr(dir)+`}`, &res)
	if res.Endpoint["securityPolicy"] != "None" || res.SessionTimeoutMs != 1200000 || con.cfg.Certificate != nil {
		t.Fatalf("%+v", res)
	}
	// with user: highest level is Basic256Sha256, a certificate is created
	line := `{"id":2,"cmd":"connect","url":"opc.tcp://fake:4840","securityPolicy":"auto","securityMode":"auto",` +
		`"user":"demo","password":"secret","certFile":null,"keyFile":null,"certDir":` + jsonStr(dir) + `,` +
		`"applicationName":"JEVis Test","applicationUri":"urn:jevis:test","trustServerCertificate":true,"timeoutMs":5000}`
	ok(t, b, line, &res)
	if res.Endpoint["securityPolicy"] != "Basic256Sha256" || con.cfg.Certificate == nil || con.cfg.User != "demo" {
		t.Fatalf("%+v %+v", res, con.cfg)
	}
	if certs.CertificateURI(con.cfg.Certificate) != "urn:jevis:test" || con.cfg.Timeout != 5*time.Second {
		t.Fatal("certificate URI or timeout")
	}
	if _, err := os.Stat(filepath.Join(dir, certs.CertFileName)); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(logBuf.String(), "secret") {
		t.Fatal("password logged")
	}
	// wrong password
	fails(t, b, strings.Replace(line, "secret", "wrong", 1), "connect_failed", "BadUserAccessDenied")
	// explicit endpoint that does not exist
	fails(t, b, `{"id":4,"cmd":"connect","url":"opc.tcp://fake:4840","securityPolicy":"Basic256","securityMode":"Sign"}`, "connect_failed", "")
	fails(t, b, `{"id":5,"cmd":"connect","url":"opc.tcp://fake:4840","securityPolicy":"Basic512"}`, "bad_request", "")
}

func TestConnectUntrustedServer(t *testing.T) {
	con, _ := newFake()
	b := New(con, nil)
	dir := t.TempDir()
	line := `{"id":1,"cmd":"connect","url":"opc.tcp://fake:4840","user":"demo","password":"secret",` +
		`"certDir":` + jsonStr(dir) + `,"trustServerCertificate":false}`
	fails(t, b, line, "connect_failed", "BadCertificateUntrusted")
	rejected := filepath.Join(dir, certs.RejectedDir, certs.Thumbprint(serverCert)+".der")
	if _, err := os.Stat(rejected); err != nil {
		t.Fatal(err)
	}
	_ = os.MkdirAll(filepath.Join(dir, certs.TrustedDir), 0o700)
	if err := os.Rename(rejected, filepath.Join(dir, certs.TrustedDir, "server.der")); err != nil {
		t.Fatal(err)
	}
	ok(t, b, line, nil)
}

func jsonStr(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}

func TestBrowse(t *testing.T) {
	b, _, cl := connected(t)
	var refs []map[string]any
	ok(t, b, `{"id":2,"cmd":"browse","nodeId":"i=85","nodeClasses":["Object","Variable"],"references":"Hierarchical"}`, &refs)
	if len(refs) != 5 {
		t.Fatalf("%d refs (continuation points not followed?)", len(refs))
	}
	want := map[string]any{"nodeId": "ns=2;s=Device0", "browseName": "2:Device0", "displayName": "Device 0",
		"nodeClass": "Object", "typeDefinition": "i=58", "referenceType": "i=35"}
	for k, v := range want {
		if refs[0][k] != v {
			t.Errorf("%s: %v want %v", k, refs[0][k], v)
		}
	}
	d := cl.browseReqs[0].NodesToBrowse[0]
	if d.NodeClassMask != uint32(ua.NodeClassObject|ua.NodeClassVariable) || d.ReferenceTypeID.IntID() != 33 ||
		!d.IncludeSubtypes || d.BrowseDirection != ua.BrowseDirectionForward {
		t.Fatalf("%+v", d)
	}
	ok(t, b, `{"id":3,"cmd":"browse","nodeId":"i=85","references":"All"}`, &refs)
	if d := cl.browseReqs[1].NodesToBrowse[0]; d.NodeClassMask != 0 || d.ReferenceTypeID.IntID() != 31 {
		t.Fatalf("%+v", d)
	}
	fails(t, b, `{"id":4,"cmd":"browse","nodeId":"ns=9;s=missing"}`, "opcua", "BadNodeIdUnknown")
	fails(t, b, `{"id":5,"cmd":"browse","nodeId":"i=85","nodeClasses":["Thing"]}`, "bad_request", "")
}

func TestRead(t *testing.T) {
	b, _, _ := connected(t)
	var res []map[string]any
	ok(t, b, `{"id":2,"cmd":"read","nodeIds":["ns=2;s=Temp","ns=2;s=Bad","ns=2;s=Gone"],"attribute":"Value"}`, &res)
	want := map[string]any{"nodeId": "ns=2;s=Temp", "value": 21.5, "dataType": "Double",
		"sourceTimestamp": "2026-11-12T08:00:00.000Z", "serverTimestamp": "2026-11-12T08:00:00.120Z",
		"status": "Good", "statusCode": 0.0}
	for k, v := range want {
		if res[0][k] != v {
			t.Errorf("%s: %v want %v", k, res[0][k], v)
		}
	}
	if res[1]["status"] != "BadNoCommunication" || res[1]["value"] != nil || res[2]["status"] != "BadNodeIdUnknown" {
		t.Fatalf("%v", res)
	}
	ok(t, b, `{"id":3,"cmd":"read","nodeIds":["ns=2;s=Temp","ns=2;s=Struct"],"attribute":"DataType"}`, &res)
	if res[0]["value"] != "Double" || res[1]["value"] != "ns=3;i=3001" {
		t.Fatalf("%v", res)
	}
	ok(t, b, `{"id":4,"cmd":"read","nodeIds":["ns=2;s=Temp"],"attribute":"NodeClass"}`, &res)
	if res[0]["value"] != "Variable" {
		t.Fatalf("%v", res)
	}
	ok(t, b, `{"id":5,"cmd":"read","nodeIds":["ns=2;s=Temp"],"attribute":"DisplayName"}`, &res)
	if res[0]["value"] != "Temp" {
		t.Fatalf("%v", res)
	}
	ok(t, b, `{"id":6,"cmd":"read","nodeIds":["ns=2;s=Temp"]}`, &res)
	if res[0]["value"] != 21.5 {
		t.Fatalf("default attribute: %v", res)
	}
	fails(t, b, `{"id":7,"cmd":"read","nodeIds":["ns=2;s=Temp"],"attribute":"Colour"}`, "bad_request", "")
	fails(t, b, `{"id":8,"cmd":"read","nodeIds":[]}`, "bad_request", "")
}

func TestWrite(t *testing.T) {
	b, _, cl := connected(t)
	var res map[string]any
	ok(t, b, `{"id":2,"cmd":"write","nodeId":"ns=2;s=Temp","value":21.0,"dataType":"Double"}`, &res)
	if res["status"] != "Good" || res["statusCode"] != 0.0 {
		t.Fatalf("%v", res)
	}
	if v := cl.writes[0].Value.Value; v.Type() != ua.TypeIDDouble || v.Float() != 21 || cl.writes[0].AttributeID != ua.AttributeIDValue {
		t.Fatalf("%v", v)
	}
	ok(t, b, `{"id":3,"cmd":"write","nodeId":"ns=2;s=Count","value":7,"dataType":"auto"}`, &res)
	if v := cl.writes[1].Value.Value; v.Type() != ua.TypeIDInt32 || v.Int() != 7 {
		t.Fatalf("auto: %v", v)
	}
	ok(t, b, `{"id":4,"cmd":"write","nodeId":"ns=2;s=Duration","value":"1500"}`, &res)
	if v := cl.writes[2].Value.Value; v.Type() != ua.TypeIDDouble || v.Float() != 1500 {
		t.Fatalf("auto subtype: %v", v)
	}
	ok(t, b, `{"id":5,"cmd":"write","nodeId":"ns=2;s=Temp","value":[1,2],"dataType":"Float"}`, nil)
	if v := cl.writes[3].Value.Value; v.Type() != ua.TypeIDFloat || v.ArrayLength() != 2 {
		t.Fatalf("array: %v", v)
	}
	fails(t, b, `{"id":6,"cmd":"write","nodeId":"ns=2;s=Struct","value":1,"dataType":"auto"}`, "bad_request", "")
	fails(t, b, `{"id":7,"cmd":"write","nodeId":"ns=2;s=Count","value":"x","dataType":"Int32"}`, "bad_request", "")
	fails(t, b, `{"id":8,"cmd":"write","nodeId":"ns=2;s=Count","value":1,"dataType":"Guid"}`, "bad_request", "")
	fails(t, b, `{"id":9,"cmd":"write","nodeId":"ns=2;s=Count","value":null,"dataType":"Int32"}`, "bad_request", "")
	fails(t, b, `{"id":10,"cmd":"write","nodeId":"ns=2;s=Nothing","value":1,"dataType":"auto"}`, "opcua", "BadAttributeIdInvalid")
	cl.writeRes = ua.StatusBadNotWritable
	fails(t, b, `{"id":11,"cmd":"write","nodeId":"ns=2;s=Temp","value":1,"dataType":"Double"}`, "opcua", "BadNotWritable")
}

type histResult struct {
	Values []struct {
		TS         string `json:"ts"`
		Value      any    `json:"value"`
		Status     string `json:"status"`
		StatusCode uint32 `json:"statusCode"`
	} `json:"values"`
	Truncated bool `json:"truncated"`
}

func TestHistoryRaw(t *testing.T) {
	b, _, cl := connected(t)
	var res histResult
	ok(t, b, `{"id":2,"cmd":"history-raw","nodeId":"ns=2;s=Temp","from":"2026-11-12T09:00:00+01:00","to":"2026-11-13T00:00:00Z",`+
		`"maxValues":0,"returnBounds":true,"timestamps":"Source"}`, &res)
	if len(res.Values) != 10 || res.Truncated {
		t.Fatalf("%d values, truncated %v", len(res.Values), res.Truncated)
	}
	if res.Values[1].TS != "2026-11-12T08:15:00.000Z" || res.Values[9].Value != 9.0 || res.Values[0].Status != "Good" {
		t.Fatalf("%+v", res.Values[1])
	}
	if len(cl.histReqs) != 4 {
		t.Fatalf("%d calls, want 4 (3 per call)", len(cl.histReqs))
	}
	req := cl.histReqs[0]
	d := req.HistoryReadDetails.Value.(*ua.ReadRawModifiedDetails)
	if req.TimestampsToReturn != ua.TimestampsToReturnSource || d.IsReadModified || !d.ReturnBounds || d.NumValuesPerNode != 0 ||
		!d.StartTime.Equal(t0) || req.HistoryReadDetails.TypeID.NodeID.IntID() != 649 {
		t.Fatalf("%+v %+v", req, d)
	}

	// maxValues truncates and releases the continuation point
	cl.histReqs = nil
	ok(t, b, `{"id":3,"cmd":"history-raw","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z",`+
		`"maxValues":4,"valuesPerCall":2,"timestamps":"Server","returnBounds":false}`, &res)
	if len(res.Values) != 4 || !res.Truncated || res.Values[0].TS != "2026-11-12T08:00:01.000Z" {
		t.Fatalf("%+v", res)
	}
	last := cl.histReqs[len(cl.histReqs)-1]
	if len(cl.histReqs) != 3 || !last.ReleaseContinuationPoints || string(last.NodesToRead[0].ContinuationPoint) != "4" {
		t.Fatalf("%d calls, release %v", len(cl.histReqs), last.ReleaseContinuationPoints)
	}
	if d := cl.histReqs[0].HistoryReadDetails.Value.(*ua.ReadRawModifiedDetails); d.NumValuesPerNode != 2 || d.ReturnBounds {
		t.Fatalf("%+v", d)
	}
	// maxValues equal to the number of values: not truncated
	ok(t, b, `{"id":4,"cmd":"history-raw","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z","maxValues":10}`, &res)
	if len(res.Values) != 10 || res.Truncated {
		t.Fatalf("%d %v", len(res.Values), res.Truncated)
	}

	cl.historyErr = ua.StatusBadNoData
	ok(t, b, `{"id":5,"cmd":"history-raw","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z"}`, &res)
	if len(res.Values) != 0 {
		t.Fatal("BadNoData must give an empty list")
	}
	cl.historyErr = ua.StatusBadHistoryOperationUnsupported
	fails(t, b, `{"id":6,"cmd":"history-raw","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z"}`,
		"opcua", "BadHistoryOperationUnsupported")
	fails(t, b, `{"id":7,"cmd":"history-raw","nodeId":"ns=2;s=Temp","from":"yesterday","to":"2026-11-13T00:00:00Z"}`, "bad_request", "")
	fails(t, b, `{"id":8,"cmd":"history-raw","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z","timestamps":"Neither"}`, "bad_request", "")
}

func TestHistoryProcessed(t *testing.T) {
	b, _, cl := connected(t)
	var res histResult
	ok(t, b, `{"id":2,"cmd":"history-processed","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z",`+
		`"aggregate":"i=2342","intervalMs":3600000,"useServerCapabilitiesDefaults":true,"treatUncertainAsBad":false,`+
		`"percentDataBad":0,"percentDataGood":0,"useSlopedExtrapolation":false}`, &res)
	if len(res.Values) != 10 {
		t.Fatalf("%d values", len(res.Values))
	}
	req := cl.histReqs[0]
	d := req.HistoryReadDetails.Value.(*ua.ReadProcessedDetails)
	if d.ProcessingInterval != 3600000 || d.AggregateType[0].IntID() != 2342 || !d.AggregateConfiguration.UseServerCapabilitiesDefaults ||
		req.HistoryReadDetails.TypeID.NodeID.IntID() != 652 {
		t.Fatalf("%+v", d)
	}
	cl.histReqs = nil
	ok(t, b, `{"id":3,"cmd":"history-processed","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z",`+
		`"aggregate":"i=2342","intervalMs":900000,"useServerCapabilitiesDefaults":false,"percentDataBad":20,"percentDataGood":80}`, &res)
	d = cl.histReqs[0].HistoryReadDetails.Value.(*ua.ReadProcessedDetails)
	if c := d.AggregateConfiguration; c.UseServerCapabilitiesDefaults || c.PercentDataBad != 20 || c.PercentDataGood != 80 {
		t.Fatalf("%+v", c)
	}
	fails(t, b, `{"id":4,"cmd":"history-processed","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z","aggregate":"i=2342"}`, "bad_request", "")
	fails(t, b, `{"id":5,"cmd":"history-processed","nodeId":"ns=2;s=Temp","from":"2026-11-12T00:00:00Z","to":"2026-11-13T00:00:00Z","intervalMs":1}`, "bad_request", "")
}

func TestServeLoop(t *testing.T) {
	con, cl := newFake()
	b := New(con, nil)
	in := strings.Join([]string{
		`{"id":1,"cmd":"read","nodeIds":["ns=2;s=Temp"]}`,
		`{"id":2,"cmd":"connect","url":"opc.tcp://fake:4840","securityPolicy":"None"}`,
		``,
		`{"id":3,"cmd":"read","nodeIds":["ns=2;s=Temp"]}`,
		`{"id":4,"cmd":"close"}`,
		`{"id":5,"cmd":"read","nodeIds":["ns=2;s=Temp"]}`,
	}, "\n")
	var out bytes.Buffer
	if err := b.Serve(strings.NewReader(in), &out); err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSpace(out.String()), "\n")
	if len(lines) != 4 {
		t.Fatalf("%d lines: %s", len(lines), out.String())
	}
	for i, l := range lines {
		var r response
		if err := json.Unmarshal([]byte(l), &r); err != nil {
			t.Fatal(err)
		}
		if string(r.ID) != fmt.Sprint(i+1) {
			t.Fatalf("line %d has id %s", i, r.ID)
		}
		if wantOK := i > 0; r.OK != wantOK {
			t.Fatalf("line %d: %s", i, l)
		}
	}
	if lines[3] != `{"id":4,"ok":true,"result":{}}` {
		t.Fatal(lines[3])
	}
	if !cl.closed {
		t.Fatal("session not closed")
	}

	// EOF closes the session as well
	con2, cl2 := newFake()
	b2 := New(con2, nil)
	out.Reset()
	if err := b2.Serve(strings.NewReader(`{"id":1,"cmd":"connect","url":"opc.tcp://fake:4840","securityPolicy":"None"}`), &out); err != nil {
		t.Fatal(err)
	}
	if !cl2.closed || !strings.Contains(out.String(), `"ok":true`) {
		t.Fatal("EOF did not close the session")
	}
}

func TestTimeoutPerRequest(t *testing.T) {
	b, _, _ := connected(t)
	if b.timeoutOf(commonWith(nil)) != DefaultTimeout {
		t.Fatal("default timeout")
	}
	ms := int64(250)
	if b.timeoutOf(commonWith(&ms)) != 250*time.Millisecond {
		t.Fatal("request timeout")
	}
	if e := uaError(context.DeadlineExceeded, "internal"); e.Code != "timeout" {
		t.Fatal(e)
	}
	if e := uaError(ua.StatusBadTimeout, "internal"); e.Code != "timeout" || *e.Status != "BadTimeout" {
		t.Fatal(e)
	}
	if e := uaError(fmt.Errorf("wrapped: %w", ua.StatusBadSessionIDInvalid), "internal"); e.Code != "opcua" || *e.Status != "BadSessionIdInvalid" {
		t.Fatal(e)
	}
}

func commonWith(ms *int64) protocol.Common { return protocol.Common{TimeoutMs: ms} }
