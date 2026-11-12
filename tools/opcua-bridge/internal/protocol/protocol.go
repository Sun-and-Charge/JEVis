// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

// Package protocol implements the JSON lines protocol between JEVis and the
// OPC UA bridge: one request object per input line, one response object per
// output line.
package protocol

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"reflect"
	"strings"
	"sync"
)

// Error codes of the protocol.
const (
	CodeBadRequest    = "bad_request"
	CodeNotConnected  = "not_connected"
	CodeConnectFailed = "connect_failed"
	CodeTimeout       = "timeout"
	CodeOPCUA         = "opcua"
	CodeInternal      = "internal"
)

// Error is the error object of a failed response.
type Error struct {
	Code    string  `json:"code"`
	Message string  `json:"message"`
	Status  *string `json:"status"`
}

func (e *Error) Error() string {
	if e.Status != nil {
		return fmt.Sprintf("%s: %s (%s)", e.Code, e.Message, *e.Status)
	}
	return e.Code + ": " + e.Message
}

// Errorf creates an error without OPC UA status.
func Errorf(code, format string, args ...any) *Error {
	return &Error{Code: code, Message: fmt.Sprintf(format, args...)}
}

// WithStatus creates an error carrying an OPC UA status name.
func WithStatus(code, message, status string) *Error {
	return &Error{Code: code, Message: message, Status: &status}
}

// Request is one parsed request line. Parameters are decoded lazily with
// Decode into a command specific struct.
type Request struct {
	ID  json.RawMessage
	Cmd string
	raw []byte
}

// Common holds the fields every request may carry. Command parameter structs
// embed it so that strict decoding accepts them.
type Common struct {
	ID        json.RawMessage `json:"id"`
	Cmd       string          `json:"cmd"`
	TimeoutMs *int64          `json:"timeoutMs"`
}

// Parse parses one request line. The returned request is non-nil whenever an
// id could be extracted, so the error response can carry it.
func Parse(line []byte) (*Request, *Error) {
	var head struct {
		ID  json.RawMessage `json:"id"`
		Cmd *string         `json:"cmd"`
	}
	trimmed := bytes.TrimSpace(line)
	if len(trimmed) == 0 || trimmed[0] != '{' {
		return &Request{}, Errorf(CodeBadRequest, "request must be a JSON object")
	}
	if err := json.Unmarshal(trimmed, &head); err != nil {
		return &Request{}, Errorf(CodeBadRequest, "invalid JSON: %v", err)
	}
	req := &Request{ID: head.ID, raw: trimmed}
	if head.Cmd == nil || *head.Cmd == "" {
		return req, Errorf(CodeBadRequest, "missing cmd")
	}
	req.Cmd = *head.Cmd
	return req, nil
}

// Decode decodes the request parameters into v strictly: unknown fields are an
// error. Numbers are kept as json.Number where v uses interface values.
func (r *Request) Decode(v any) *Error {
	dec := json.NewDecoder(bytes.NewReader(r.raw))
	dec.DisallowUnknownFields()
	dec.UseNumber()
	if err := dec.Decode(v); err != nil {
		msg := err.Error()
		msg = trimJSONPrefix(msg)
		return Errorf(CodeBadRequest, "%s: %s", r.Cmd, msg)
	}
	// encoding/json matches field names case-insensitively; the protocol
	// does not, so check the exact names.
	var keys map[string]json.RawMessage
	if err := json.Unmarshal(r.raw, &keys); err != nil {
		return Errorf(CodeBadRequest, "%s: %v", r.Cmd, err)
	}
	known := fieldNames(reflect.TypeOf(v))
	for k := range keys {
		if !known[k] {
			return Errorf(CodeBadRequest, "%s: unknown field %q", r.Cmd, k)
		}
	}
	return nil
}

// fieldNames returns the JSON names of a struct's fields, including those of
// embedded structs.
func fieldNames(t reflect.Type) map[string]bool {
	for t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	names := map[string]bool{}
	if t.Kind() != reflect.Struct {
		return names
	}
	for i := 0; i < t.NumField(); i++ {
		f := t.Field(i)
		tag := f.Tag.Get("json")
		if f.Anonymous && tag == "" {
			for n := range fieldNames(f.Type) {
				names[n] = true
			}
			continue
		}
		name, _, _ := strings.Cut(tag, ",")
		if name == "-" {
			continue
		}
		if name == "" {
			name = f.Name
		}
		names[name] = true
	}
	return names
}

func trimJSONPrefix(s string) string {
	const p = "json: "
	if len(s) > len(p) && s[:len(p)] == p {
		return s[len(p):]
	}
	return s
}

// Response is one response line.
type Response struct {
	ID     json.RawMessage `json:"id"`
	OK     bool            `json:"ok"`
	Result any             `json:"result,omitempty"`
	Error  *Error          `json:"error,omitempty"`
}

// Success creates a successful response.
func Success(id json.RawMessage, result any) *Response {
	if result == nil {
		result = struct{}{}
	}
	return &Response{ID: normID(id), OK: true, Result: result}
}

// Failure creates a failed response.
func Failure(id json.RawMessage, err *Error) *Response {
	return &Response{ID: normID(id), OK: false, Error: err}
}

func normID(id json.RawMessage) json.RawMessage {
	if len(id) == 0 {
		return json.RawMessage("null")
	}
	return id
}

// Writer writes response lines; every line is written in one call so that a
// reader on the other side of a pipe sees complete lines.
type Writer struct {
	mu sync.Mutex
	w  io.Writer
}

// NewWriter creates a response writer.
func NewWriter(w io.Writer) *Writer { return &Writer{w: w} }

// Write encodes the response as one line and flushes it.
func (w *Writer) Write(resp *Response) error {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(resp); err != nil {
		// Encoding a result failed (should not happen); answer with an internal error.
		buf.Reset()
		fail := Failure(resp.ID, Errorf(CodeInternal, "cannot encode response: %v", err))
		if err2 := enc.Encode(fail); err2 != nil {
			return err2
		}
	}
	w.mu.Lock()
	defer w.mu.Unlock()
	_, err := w.w.Write(buf.Bytes())
	if f, ok := w.w.(interface{ Flush() error }); ok && err == nil {
		err = f.Flush()
	}
	return err
}

// Reader reads request lines of any length.
type Reader struct {
	r *bufio.Reader
}

// NewReader creates a request line reader.
func NewReader(r io.Reader) *Reader { return &Reader{r: bufio.NewReaderSize(r, 64*1024)} }

// Next returns the next non-empty line without line terminator, or io.EOF.
func (r *Reader) Next() ([]byte, error) {
	for {
		line, err := r.r.ReadBytes('\n')
		line = bytes.TrimRight(line, "\r\n")
		if len(bytes.TrimSpace(line)) > 0 {
			return line, nil
		}
		if err != nil {
			if errors.Is(err, io.EOF) {
				return nil, io.EOF
			}
			return nil, err
		}
	}
}
