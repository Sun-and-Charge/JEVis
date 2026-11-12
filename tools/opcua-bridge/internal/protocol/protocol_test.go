// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package protocol

import (
	"bytes"
	"encoding/json"
	"io"
	"strings"
	"testing"
)

func TestParse(t *testing.T) {
	req, err := Parse([]byte(` {"id":7,"cmd":"read","nodeIds":["i=84"]} `))
	if err != nil {
		t.Fatal(err)
	}
	if string(req.ID) != "7" || req.Cmd != "read" {
		t.Fatalf("got id %s cmd %s", req.ID, req.Cmd)
	}
}

func TestParseErrors(t *testing.T) {
	cases := map[string]string{
		`not json`:           "",
		`[1,2]`:              "",
		`{"id":3`:            "",
		`{"id":4}`:           "4",
		`{"id":5,"cmd":""}`:  "5",
		`{"id":"x","cmd":1}`: "",
	}
	for line, wantID := range cases {
		req, err := Parse([]byte(line))
		if err == nil {
			t.Errorf("%s: expected error", line)
			continue
		}
		if err.Code != CodeBadRequest {
			t.Errorf("%s: code %s", line, err.Code)
		}
		if string(req.ID) != wantID {
			t.Errorf("%s: id %q, want %q", line, req.ID, wantID)
		}
	}
}

type params struct {
	Common
	NodeID string `json:"nodeId"`
	Value  any    `json:"value"`
}

func TestDecodeStrict(t *testing.T) {
	req, _ := Parse([]byte(`{"id":1,"cmd":"write","nodeId":"i=1","value":12345678901234567890,"timeoutMs":500}`))
	var p params
	if err := req.Decode(&p); err != nil {
		t.Fatal(err)
	}
	if n, ok := p.Value.(json.Number); !ok || n.String() != "12345678901234567890" {
		t.Fatalf("value %T %v", p.Value, p.Value)
	}
	if p.TimeoutMs == nil || *p.TimeoutMs != 500 {
		t.Fatalf("timeoutMs %v", p.TimeoutMs)
	}

	req, _ = Parse([]byte(`{"id":1,"cmd":"write","nodeID":"i=1"}`))
	err := req.Decode(&p)
	if err == nil || err.Code != CodeBadRequest || !strings.Contains(err.Message, "nodeID") {
		t.Fatalf("unknown field accepted: %v", err)
	}
}

func TestResponses(t *testing.T) {
	var buf bytes.Buffer
	w := NewWriter(&buf)
	_ = w.Write(Success(json.RawMessage("7"), []int{1}))
	_ = w.Write(Success(nil, nil))
	_ = w.Write(Failure(json.RawMessage(`"a"`), WithStatus(CodeOPCUA, "x <y>", "BadNodeIdUnknown")))
	_ = w.Write(Failure(json.RawMessage("9"), Errorf(CodeNotConnected, "not connected")))
	want := `{"id":7,"ok":true,"result":[1]}
{"id":null,"ok":true,"result":{}}
{"id":"a","ok":false,"error":{"code":"opcua","message":"x <y>","status":"BadNodeIdUnknown"}}
{"id":9,"ok":false,"error":{"code":"not_connected","message":"not connected","status":null}}
`
	if buf.String() != want {
		t.Fatalf("got\n%s\nwant\n%s", buf.String(), want)
	}
}

func TestReader(t *testing.T) {
	long := strings.Repeat("x", 200000)
	r := NewReader(strings.NewReader("a\r\n\n  \n" + long + "\nlast"))
	for _, want := range []string{"a", long, "last"} {
		got, err := r.Next()
		if err != nil || string(got) != want {
			t.Fatalf("got %d bytes, err %v", len(got), err)
		}
	}
	if _, err := r.Next(); err != io.EOF {
		t.Fatalf("expected EOF, got %v", err)
	}
}
