// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

//go:build integration

// Package integration drives the built opcua-bridge binary through stdin and
// stdout against the asyncua test server in ../testserver.
//
// Run: go test -tags integration ./integration/
// The test server must listen on opc.tcp://127.0.0.1:48400, or set
// OPCUA_TEST_PYTHON to a Python with asyncua and the test starts it.
package integration

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"
)

const (
	serverURL  = "opc.tcp://127.0.0.1:48400"
	serverAddr = "127.0.0.1:48400"
	dayFrom    = "2026-11-01T00:00:00Z"
	dayTo      = "2026-11-02T00:00:00Z"
)

var bridgeBin string

func TestMain(m *testing.M) {
	os.Exit(run(m))
}

func run(m *testing.M) int {
	tmp, err := os.MkdirTemp("", "opcua-bridge-it-")
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	defer os.RemoveAll(tmp)
	bridgeBin = filepath.Join(tmp, "opcua-bridge")
	if runtime.GOOS == "windows" {
		bridgeBin += ".exe"
	}
	build := exec.Command("go", "build", "-ldflags", "-X main.version=it-test", "-o", bridgeBin, "..")
	build.Stdout, build.Stderr = os.Stderr, os.Stderr
	if err := build.Run(); err != nil {
		fmt.Fprintln(os.Stderr, "building the bridge:", err)
		return 1
	}
	stop, err := ensureServer()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	defer stop()
	return m.Run()
}

// ensureServer uses a running test server or starts one with OPCUA_TEST_PYTHON.
func ensureServer() (func(), error) {
	if c, err := net.DialTimeout("tcp", serverAddr, time.Second); err == nil {
		c.Close()
		return func() {}, nil
	}
	py := os.Getenv("OPCUA_TEST_PYTHON")
	if py == "" {
		return nil, fmt.Errorf("no OPC UA test server on %s; start testserver/server.py or set OPCUA_TEST_PYTHON (see testserver/README.md)", serverAddr)
	}
	// --exit-on-eof: closing stdin stops the server. Killing is not enough on
	// Windows, where a venv's python.exe is a launcher with a child process.
	cmd := exec.Command(py, "-I", filepath.Join("..", "testserver", "server.py"), "--exit-on-eof")
	out, err := cmd.StdoutPipe()
	if err != nil {
		return nil, err
	}
	in, err := cmd.StdinPipe()
	if err != nil {
		return nil, err
	}
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("starting the test server: %v", err)
	}
	stop := func() {
		_ = in.Close()
		done := make(chan struct{})
		go func() { _ = cmd.Wait(); close(done) }()
		select {
		case <-done:
		case <-time.After(15 * time.Second):
			_ = cmd.Process.Kill()
		}
	}
	ready := make(chan error, 1)
	go func() {
		sc := bufio.NewScanner(out)
		for sc.Scan() {
			if strings.HasPrefix(sc.Text(), "READY") {
				ready <- nil
				io.Copy(io.Discard, out)
				return
			}
		}
		ready <- fmt.Errorf("test server exited before READY")
	}()
	select {
	case err := <-ready:
		if err != nil {
			stop()
			return nil, err
		}
	case <-time.After(60 * time.Second):
		stop()
		return nil, fmt.Errorf("test server did not start within 60s")
	}
	return stop, nil
}

// ---- bridge process ----

type bridge struct {
	t      *testing.T
	cmd    *exec.Cmd
	in     io.WriteCloser
	out    *bufio.Reader
	stderr *lockedBuffer
	nextID *int
}

// with returns the same bridge process reporting to another (sub)test.
func (b *bridge) with(t *testing.T) *bridge {
	c := *b
	c.t = t
	return &c
}

type lockedBuffer struct {
	mu sync.Mutex
	b  bytes.Buffer
}

func (l *lockedBuffer) Write(p []byte) (int, error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.b.Write(p)
}

func (l *lockedBuffer) String() string {
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.b.String()
}

func start(t *testing.T) *bridge {
	t.Helper()
	cmd := exec.Command(bridgeBin)
	in, err := cmd.StdinPipe()
	if err != nil {
		t.Fatal(err)
	}
	out, err := cmd.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	errBuf := &lockedBuffer{}
	cmd.Stderr = errBuf
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	b := &bridge{t: t, cmd: cmd, in: in, out: bufio.NewReaderSize(out, 1<<20), stderr: errBuf, nextID: new(int)}
	t.Cleanup(func() {
		_ = in.Close()
		done := make(chan struct{})
		go func() { _ = cmd.Wait(); close(done) }()
		select {
		case <-done:
		case <-time.After(10 * time.Second):
			_ = cmd.Process.Kill()
		}
		if t.Failed() {
			t.Logf("bridge stderr:\n%s", errBuf.String())
		}
	})
	return b
}

type response struct {
	ID     int             `json:"id"`
	OK     bool            `json:"ok"`
	Result json.RawMessage `json:"result"`
	Error  *struct {
		Code    string  `json:"code"`
		Message string  `json:"message"`
		Status  *string `json:"status"`
	} `json:"error"`
}

// send sends one request and reads exactly one response line.
func (b *bridge) send(cmd string, params map[string]any) response {
	b.t.Helper()
	*b.nextID++
	id := *b.nextID
	req := map[string]any{"id": id, "cmd": cmd}
	for k, v := range params {
		req[k] = v
	}
	line, _ := json.Marshal(req)
	if _, err := b.in.Write(append(line, '\n')); err != nil {
		b.t.Fatalf("%s: write: %v", cmd, err)
	}
	type lineResult struct {
		line []byte
		err  error
	}
	ch := make(chan lineResult, 1)
	go func() {
		l, err := b.out.ReadBytes('\n')
		ch <- lineResult{l, err}
	}()
	var lr lineResult
	select {
	case lr = <-ch:
	case <-time.After(60 * time.Second):
		b.t.Fatalf("%s: no response within 60s", cmd)
	}
	if lr.err != nil {
		b.t.Fatalf("%s: read: %v", cmd, lr.err)
	}
	var r response
	if err := json.Unmarshal(lr.line, &r); err != nil {
		b.t.Fatalf("%s: stdout line is not a response: %q", cmd, lr.line)
	}
	if r.ID != id {
		b.t.Fatalf("%s: response id %d, want %d", cmd, r.ID, id)
	}
	return r
}

func (b *bridge) ok(cmd string, params map[string]any, result any) {
	b.t.Helper()
	r := b.send(cmd, params)
	if !r.OK {
		b.t.Fatalf("%s %v: %+v", cmd, params, *r.Error)
	}
	if result != nil {
		if err := json.Unmarshal(r.Result, result); err != nil {
			b.t.Fatalf("%s: result %s: %v", cmd, r.Result, err)
		}
	}
}

func (b *bridge) fails(cmd string, params map[string]any, code, status string) {
	b.t.Helper()
	r := b.send(cmd, params)
	if r.OK {
		b.t.Fatalf("%s: expected %s, got %s", cmd, code, r.Result)
	}
	got := ""
	if r.Error.Status != nil {
		got = *r.Error.Status
	}
	if r.Error.Code != code || (status != "" && got != status) {
		b.t.Fatalf("%s: got %s/%s (%s), want %s/%s", cmd, r.Error.Code, got, r.Error.Message, code, status)
	}
}

type endpointDesc struct {
	URL               string   `json:"url"`
	SecurityPolicyURI string   `json:"securityPolicyUri"`
	SecurityPolicy    string   `json:"securityPolicy"`
	SecurityMode      string   `json:"securityMode"`
	SecurityLevel     int      `json:"securityLevel"`
	UserTokens        []string `json:"userTokens"`
	ServerCertificate *string  `json:"serverCertificate"`
}

type connectResult struct {
	Endpoint         endpointDesc `json:"endpoint"`
	SessionTimeoutMs int64        `json:"sessionTimeoutMs"`
}

// testNamespace is the namespace URI of the test server's nodes.
const testNamespace = "urn:jevis:opcua-bridge:testserver"

var nsIndex = -1

// n returns the NodeId of a test server node; bn its browse name.
func n(id string) string    { return fmt.Sprintf("ns=%d;s=%s", nsIndex, id) }
func bn(name string) string { return fmt.Sprintf("%d:%s", nsIndex, name) }

// connect connects and resolves the namespace index of the test server's nodes
// from the NamespaceArray (i=2255).
func (b *bridge) connect(params map[string]any, result any) {
	b.t.Helper()
	b.ok("connect", params, result)
	var res []struct {
		Value []string `json:"value"`
	}
	b.ok("read", map[string]any{"nodeIds": []string{"i=2255"}}, &res)
	for i, u := range res[0].Value {
		if u == testNamespace {
			nsIndex = i
			return
		}
	}
	b.t.Fatalf("namespace %s not in %v", testNamespace, res[0].Value)
}

func connectParams(certDir string, extra map[string]any) map[string]any {
	p := map[string]any{
		"url": serverURL, "securityPolicy": "auto", "securityMode": "auto", "user": nil, "password": nil,
		"certFile": nil, "keyFile": nil, "certDir": certDir, "applicationName": "JEVis Integration Test",
		"applicationUri": "urn:jevis:integration-test", "trustServerCertificate": true, "timeoutMs": 20000,
	}
	for k, v := range extra {
		p[k] = v
	}
	return p
}

// ---- tests ----

func TestVersion(t *testing.T) {
	out, err := exec.Command(bridgeBin, "--version").Output()
	if err != nil || !strings.HasPrefix(string(out), "opcua-bridge it-test") {
		t.Fatalf("%q %v", out, err)
	}
}

func TestEndpoints(t *testing.T) {
	b := start(t)
	var eps []endpointDesc
	b.ok("endpoints", map[string]any{"url": serverURL}, &eps)
	found := map[string]endpointDesc{}
	for _, e := range eps {
		found[e.SecurityPolicy+"/"+e.SecurityMode] = e
	}
	none, ok1 := found["None/None"]
	sec, ok2 := found["Basic256Sha256/SignAndEncrypt"]
	if !ok1 || !ok2 {
		t.Fatalf("endpoints %+v", eps)
	}
	if strings.Join(none.UserTokens, ",") != "Anonymous,UserName" || sec.ServerCertificate == nil ||
		sec.SecurityPolicyURI != "http://opcfoundation.org/UA/SecurityPolicy#Basic256Sha256" {
		t.Fatalf("%+v %+v", none, sec)
	}
	b.fails("endpoints", map[string]any{"url": "opc.tcp://127.0.0.1:1", "timeoutMs": 3000}, "connect_failed", "")
}

func TestAnonymousNoneSession(t *testing.T) {
	b := start(t)
	b.fails("read", map[string]any{"nodeIds": []string{"i=2258"}}, "not_connected", "")
	var cr connectResult
	b.connect(connectParams(t.TempDir(), map[string]any{"securityPolicy": "None", "securityMode": "None"}), &cr)
	if cr.Endpoint.SecurityPolicy != "None" || cr.SessionTimeoutMs <= 0 {
		t.Fatalf("%+v", cr)
	}

	t.Run("browse", func(t *testing.T) {
		b := b.with(t)
		var refs []map[string]any
		b.ok("browse", map[string]any{"nodeId": "i=85", "nodeClasses": []string{"Object", "Variable"}, "references": "Hierarchical"}, &refs)
		var dev map[string]any
		for _, r := range refs {
			if r["nodeId"] == n("Device1") {
				dev = r
			}
		}
		if dev == nil || dev["browseName"] != bn("Device1") || dev["displayName"] != "Device1" || dev["nodeClass"] != "Object" ||
			dev["typeDefinition"] != "i=58" || dev["referenceType"] != "i=35" {
			t.Fatalf("Device1 not found or wrong: %v", refs)
		}
		b.ok("browse", map[string]any{"nodeId": n("Device1")}, &refs)
		names := map[string]bool{}
		for _, r := range refs {
			names[r["browseName"].(string)] = true
		}
		for _, n := range []string{bn("Temp"), bn("Count"), bn("Enabled"), bn("Name"), bn("Setpoint"), bn("Energy")} {
			if !names[n] {
				t.Errorf("%s missing in %v", n, names)
			}
		}
		b.ok("browse", map[string]any{"nodeId": "i=85", "nodeClasses": []string{"Method"}}, &refs)
		if len(refs) != 0 {
			t.Fatalf("methods below Objects: %v", refs)
		}
		// asyncua answers BadNodeIdInvalid, other servers BadNodeIdUnknown
		r := b.send("browse", map[string]any{"nodeId": n("NoSuchNode")})
		if r.OK || r.Error.Code != "opcua" || r.Error.Status == nil || !strings.HasPrefix(*r.Error.Status, "BadNodeId") {
			t.Fatalf("browse of a missing node: %+v", r)
		}
	})

	t.Run("read", func(t *testing.T) {
		b := b.with(t)
		var res []map[string]any
		b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Temp"), n("Device1.Count"), n("Device1.Enabled"),
			n("Device1.Name"), n("NoSuchNode")}, "attribute": "Value"}, &res)
		want := []struct {
			v  any
			dt string
		}{{21.5, "Double"}, {7.0, "Int32"}, {true, "Boolean"}, {"Boiler", "String"}}
		for i, w := range want {
			if res[i]["value"] != w.v || res[i]["dataType"] != w.dt || res[i]["status"] != "Good" || res[i]["statusCode"] != 0.0 {
				t.Errorf("%d: %v", i, res[i])
			}
		}
		if ts, _ := res[0]["serverTimestamp"].(string); len(ts) != 24 || !strings.HasSuffix(ts, "Z") {
			t.Errorf("serverTimestamp %v", res[0]["serverTimestamp"])
		}
		if res[4]["status"] != "BadNodeIdUnknown" || res[4]["value"] != nil {
			t.Errorf("%v", res[4])
		}
		b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Temp"), n("Device1.Count"), n("Device1.Name")}, "attribute": "DataType"}, &res)
		if res[0]["value"] != "Double" || res[1]["value"] != "Int32" || res[2]["value"] != "String" {
			t.Errorf("%v", res)
		}
		b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Energy")}, "attribute": "Historizing"}, &res)
		if res[0]["value"] != true {
			t.Errorf("Historizing %v", res)
		}
		for attr, want := range map[string]any{"DisplayName": "Energy", "BrowseName": bn("Energy"), "NodeClass": "Variable"} {
			b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Energy")}, "attribute": attr}, &res)
			if res[0]["value"] != want {
				t.Errorf("%s: %v", attr, res[0])
			}
		}
		b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Energy")}, "attribute": "AccessLevel"}, &res)
		if lvl, _ := res[0]["value"].(float64); int(lvl)&4 == 0 {
			t.Errorf("AccessLevel without HistoryRead: %v", res[0])
		}
	})

	t.Run("write", func(t *testing.T) {
		b := b.with(t)
		cases := []struct {
			node     string
			value    any
			dataType string
			back     any
		}{
			{n("Device1.Setpoint"), 22.5, "Double", 22.5},
			{n("Device1.Count"), 42, "Int32", 42.0},
			{n("Device1.Enabled"), false, "Boolean", false},
			{n("Device1.Name"), "Kessel", "String", "Kessel"},
			{n("Device1.Setpoint"), 23.25, "auto", 23.25},
			{n("Device1.Count"), "43", "auto", 43.0},
			{n("Device1.Enabled"), true, "auto", true},
		}
		for _, c := range cases {
			var st map[string]any
			b.ok("write", map[string]any{"nodeId": c.node, "value": c.value, "dataType": c.dataType}, &st)
			if st["status"] != "Good" || st["statusCode"] != 0.0 {
				t.Fatalf("%v", st)
			}
			var res []map[string]any
			b.ok("read", map[string]any{"nodeIds": []string{c.node}}, &res)
			if res[0]["value"] != c.back {
				t.Errorf("%s: read back %v, want %v", c.node, res[0]["value"], c.back)
			}
		}
		// wrong type for the variable: the server rejects it
		b.fails("write", map[string]any{"nodeId": n("Device1.Count"), "value": 1.5, "dataType": "Double"}, "opcua", "BadTypeMismatch")
		b.fails("write", map[string]any{"nodeId": n("Device1.Count"), "value": 1.5, "dataType": "Int32"}, "bad_request", "")
	})

	t.Run("history-raw", func(t *testing.T) {
		b := b.with(t)
		var res struct {
			Values []struct {
				TS     string  `json:"ts"`
				Value  float64 `json:"value"`
				Status string  `json:"status"`
			} `json:"values"`
			Truncated bool `json:"truncated"`
		}
		// 97 values, the server returns at most 20 per call: 5 calls with continuation points
		b.ok("history-raw", map[string]any{"nodeId": n("Device1.Energy"), "from": dayFrom, "to": dayTo, "maxValues": 0,
			"returnBounds": true, "timestamps": "Source"}, &res)
		if len(res.Values) != 97 || res.Truncated {
			t.Fatalf("%d values, truncated %v", len(res.Values), res.Truncated)
		}
		for i, v := range res.Values {
			wantTS := time.Date(2026, 11, 1, 0, 0, 0, 0, time.UTC).Add(time.Duration(i) * 15 * time.Minute).Format("2006-01-02T15:04:05.000Z")
			if v.TS != wantTS || v.Value != float64(i)*0.25 || v.Status != "Good" {
				t.Fatalf("value %d: %+v", i, v)
			}
		}
		// offsets in the request
		b.ok("history-raw", map[string]any{"nodeId": n("Device1.Energy"), "from": "2026-11-01T07:00:00+01:00", "to": "2026-11-01T08:00:00+01:00"}, &res)
		if len(res.Values) != 5 || res.Values[0].TS != "2026-11-01T06:00:00.000Z" {
			t.Fatalf("%+v", res.Values)
		}
		// maxValues across a continuation point
		b.ok("history-raw", map[string]any{"nodeId": n("Device1.Energy"), "from": dayFrom, "to": dayTo, "maxValues": 30}, &res)
		if len(res.Values) != 30 || !res.Truncated || res.Values[29].Value != 29*0.25 {
			t.Fatalf("%d values, truncated %v", len(res.Values), res.Truncated)
		}
		// no data in the range
		b.ok("history-raw", map[string]any{"nodeId": n("Device1.Energy"), "from": "2025-01-01T00:00:00Z", "to": "2025-01-02T00:00:00Z"}, &res)
		if len(res.Values) != 0 || res.Truncated {
			t.Fatalf("%+v", res)
		}
	})

	t.Run("history-processed", func(t *testing.T) {
		b := b.with(t)
		var res struct {
			Values []struct {
				TS     string `json:"ts"`
				Value  any    `json:"value"`
				Status string `json:"status"`
			} `json:"values"`
			Truncated bool `json:"truncated"`
		}
		b.ok("history-processed", map[string]any{"nodeId": n("Device1.Energy"), "from": dayFrom, "to": dayTo, "aggregate": "i=2342",
			"intervalMs": 3600000, "useServerCapabilitiesDefaults": true, "treatUncertainAsBad": false,
			"percentDataBad": 0, "percentDataGood": 0, "useSlopedExtrapolation": false}, &res)
		if len(res.Values) != 24 {
			t.Fatalf("%d values", len(res.Values))
		}
		for h, v := range res.Values {
			if v.Value != float64(h)+0.375 || v.Status != "Good" ||
				v.TS != time.Date(2026, 11, 1, h, 0, 0, 0, time.UTC).Format("2006-01-02T15:04:05.000Z") {
				t.Fatalf("hour %d: %+v", h, v)
			}
		}
		b.ok("history-processed", map[string]any{"nodeId": n("Device1.Energy"), "from": "2026-11-02T00:00:00Z", "to": "2026-11-02T02:00:00Z",
			"aggregate": "i=2342", "intervalMs": 3600000}, &res)
		if len(res.Values) != 2 || res.Values[1].Value != nil || res.Values[1].Status != "BadNoData" {
			t.Fatalf("%+v", res.Values)
		}
		b.fails("history-processed", map[string]any{"nodeId": n("Device1.Energy"), "from": dayFrom, "to": dayTo, "aggregate": "i=2346",
			"intervalMs": 3600000}, "opcua", "BadAggregateNotSupported")
	})

	t.Run("close", func(t *testing.T) {
		b := b.with(t)
		var res map[string]any
		b.ok("close", nil, &res)
		if len(res) != 0 {
			t.Fatalf("%v", res)
		}
		done := make(chan error, 1)
		go func() { done <- b.cmd.Wait() }()
		select {
		case err := <-done:
			if err != nil {
				t.Fatalf("exit: %v", err)
			}
		case <-time.After(10 * time.Second):
			t.Fatal("bridge did not exit after close")
		}
	})
}

func TestUserLogin(t *testing.T) {
	b := start(t)
	dir := t.TempDir()
	var cr connectResult
	// auto: the highest security level with UserName tokens is Basic256Sha256/SignAndEncrypt
	b.connect(connectParams(dir, map[string]any{"user": "demo", "password": "demo"}), &cr)
	if cr.Endpoint.SecurityPolicy != "Basic256Sha256" || cr.Endpoint.SecurityMode != "SignAndEncrypt" {
		t.Fatalf("%+v", cr.Endpoint)
	}
	for _, f := range []string{"opcua-bridge-cert.der", "opcua-bridge-key.pem"} {
		if _, err := os.Stat(filepath.Join(dir, f)); err != nil {
			t.Fatal(err)
		}
	}
	var res []map[string]any
	b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Temp")}}, &res)
	if res[0]["status"] != "Good" {
		t.Fatalf("%v", res)
	}
	// user name on the None endpoint (password encrypted with the token policy)
	b.connect(connectParams(dir, map[string]any{"user": "demo", "password": "demo", "securityPolicy": "None", "securityMode": "None"}), &cr)
	b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Temp")}}, &res)
	b.fails("connect", connectParams(dir, map[string]any{"user": "demo", "password": "wrong-password-xyz"}), "connect_failed", "")
	b.fails("read", map[string]any{"nodeIds": []string{n("Device1.Temp")}}, "not_connected", "")
	b.send("close", nil)
	_ = b.cmd.Wait()
	if strings.Contains(b.stderr.String(), "wrong-password-xyz") {
		t.Fatal("password in log output")
	}
}

func TestSignAndEncryptAnonymousWithTrust(t *testing.T) {
	b := start(t)
	dir := t.TempDir()
	params := connectParams(dir, map[string]any{"securityPolicy": "Basic256Sha256", "securityMode": "SignAndEncrypt",
		"trustServerCertificate": false})
	b.fails("connect", params, "connect_failed", "BadCertificateUntrusted")
	rejected, _ := filepath.Glob(filepath.Join(dir, "rejected", "*.der"))
	if len(rejected) != 1 {
		t.Fatalf("rejected certificates: %v", rejected)
	}
	if err := os.MkdirAll(filepath.Join(dir, "trusted"), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.Rename(rejected[0], filepath.Join(dir, "trusted", "server.der")); err != nil {
		t.Fatal(err)
	}
	var cr connectResult
	b.connect(params, &cr)
	if cr.Endpoint.SecurityPolicy != "Basic256Sha256" || cr.Endpoint.SecurityMode != "SignAndEncrypt" {
		t.Fatalf("%+v", cr.Endpoint)
	}
	cert1, _ := os.ReadFile(filepath.Join(dir, "opcua-bridge-cert.der"))
	var res []map[string]any
	b.ok("read", map[string]any{"nodeIds": []string{n("Device1.Name"), "i=2258"}}, &res)
	if res[0]["status"] != "Good" || res[1]["dataType"] != "DateTime" {
		t.Fatalf("%v", res)
	}
	var refs []map[string]any
	b.ok("browse", map[string]any{"nodeId": "i=85"}, &refs)
	if len(refs) == 0 {
		t.Fatal("empty browse")
	}

	// a second process reuses the certificate
	b2 := start(t)
	b2.connect(params, &cr)
	cert2, _ := os.ReadFile(filepath.Join(dir, "opcua-bridge-cert.der"))
	if !bytes.Equal(cert1, cert2) {
		t.Fatal("certificate was recreated")
	}
	// EOF ends the process
	_ = b2.in.Close()
	if err := b2.cmd.Wait(); err != nil {
		t.Fatalf("exit after EOF: %v", err)
	}
}

func TestCertificateFiles(t *testing.T) {
	// a certificate created by one bridge, given explicitly as certFile/keyFile
	src := t.TempDir()
	b := start(t)
	b.connect(connectParams(src, map[string]any{"securityPolicy": "Basic256Sha256"}), nil)
	b.send("close", nil)

	b2 := start(t)
	other := t.TempDir()
	var cr connectResult
	b2.connect(connectParams(other, map[string]any{"securityPolicy": "Basic256Sha256",
		"certFile": filepath.Join(src, "opcua-bridge-cert.der"), "keyFile": filepath.Join(src, "opcua-bridge-key.pem")}), &cr)
	if _, err := os.Stat(filepath.Join(other, "opcua-bridge-cert.der")); err == nil {
		t.Fatal("a certificate was created although certFile was given")
	}
	var res []map[string]any
	b2.ok("read", map[string]any{"nodeIds": []string{n("Device1.Temp")}}, &res)
}
