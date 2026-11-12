// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

// Package bridge serves the bridge protocol: it reads requests, runs them
// against one OPC UA session and writes the responses in order.
package bridge

import (
	"context"
	"errors"
	"io"
	"log"
	"net"
	"os"
	"strings"
	"time"

	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/convert"
	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/protocol"
	"github.com/gopcua/opcua/ua"
)

// DefaultTimeout applies when neither the request nor connect gave timeoutMs.
const DefaultTimeout = 30 * time.Second

// Bridge holds the state of one bridge process.
type Bridge struct {
	Connector Connector
	Log       *log.Logger

	client  Client
	timeout time.Duration
}

// New creates a bridge using the given connector.
func New(c Connector, logger *log.Logger) *Bridge {
	if logger == nil {
		logger = log.New(io.Discard, "", 0)
	}
	return &Bridge{Connector: c, Log: logger, timeout: DefaultTimeout}
}

// Serve answers requests from r on w until EOF or a close request. The
// session is closed before returning.
func (b *Bridge) Serve(r io.Reader, w io.Writer) error {
	in := protocol.NewReader(r)
	out := protocol.NewWriter(w)
	defer b.closeSession()
	for {
		line, err := in.Next()
		if err != nil {
			if errors.Is(err, io.EOF) {
				b.Log.Printf("stdin closed, exiting")
				return nil
			}
			return err
		}
		resp, stop := b.Handle(line)
		if err := out.Write(resp); err != nil {
			return err
		}
		if stop {
			return nil
		}
	}
}

// Handle answers one request line. stop is true after a close request.
func (b *Bridge) Handle(line []byte) (resp *protocol.Response, stop bool) {
	req, perr := protocol.Parse(line)
	if perr != nil {
		return protocol.Failure(req.ID, perr), false
	}
	defer func() {
		if r := recover(); r != nil {
			b.Log.Printf("panic in %s: %v", req.Cmd, r)
			resp = protocol.Failure(req.ID, protocol.Errorf(protocol.CodeInternal, "internal error: %v", r))
			stop = false
		}
	}()
	start := time.Now()
	result, perr, stop := b.dispatch(req)
	if perr != nil {
		b.Log.Printf("%s failed after %v: %s", req.Cmd, time.Since(start).Round(time.Millisecond), perr.Error())
		return protocol.Failure(req.ID, perr), stop
	}
	return protocol.Success(req.ID, result), stop
}

func (b *Bridge) dispatch(req *protocol.Request) (any, *protocol.Error, bool) {
	switch req.Cmd {
	case "endpoints":
		r, err := b.endpoints(req)
		return r, err, false
	case "connect":
		r, err := b.connect(req)
		return r, err, false
	case "browse":
		r, err := b.browse(req)
		return r, err, false
	case "read":
		r, err := b.read(req)
		return r, err, false
	case "write":
		r, err := b.write(req)
		return r, err, false
	case "history-raw":
		r, err := b.historyRaw(req)
		return r, err, false
	case "history-processed":
		r, err := b.historyProcessed(req)
		return r, err, false
	case "close":
		var p protocol.Common
		if err := req.Decode(&p); err != nil {
			return nil, err, false
		}
		b.closeSession()
		return struct{}{}, nil, true
	}
	return nil, protocol.Errorf(protocol.CodeBadRequest, "unknown command %q", req.Cmd), false
}

// context returns a context with the request's timeout, else the session's.
func (b *Bridge) context(c protocol.Common) (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), b.timeoutOf(c))
}

func (b *Bridge) timeoutOf(c protocol.Common) time.Duration {
	if c.TimeoutMs != nil && *c.TimeoutMs > 0 {
		return time.Duration(*c.TimeoutMs) * time.Millisecond
	}
	return b.timeout
}

func (b *Bridge) session() (Client, *protocol.Error) {
	if b.client == nil {
		return nil, protocol.Errorf(protocol.CodeNotConnected, "not connected")
	}
	return b.client, nil
}

func (b *Bridge) closeSession() {
	if b.client == nil {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := b.client.Close(ctx); err != nil {
		b.Log.Printf("closing session: %v", err)
	}
	b.client = nil
}

// uaError maps an error of an OPC UA call to a protocol error. code is used
// for errors that are neither timeouts nor OPC UA status codes.
func uaError(err error, code string) *protocol.Error {
	var pe *protocol.Error
	if errors.As(err, &pe) {
		return pe
	}
	var sc ua.StatusCode
	if errors.As(err, &sc) {
		name := convert.StatusName(sc)
		if sc == ua.StatusBadTimeout || sc == ua.StatusBadRequestTimeout {
			return protocol.WithStatus(protocol.CodeTimeout, err.Error(), name)
		}
		if code == protocol.CodeInternal {
			code = protocol.CodeOPCUA
		}
		return protocol.WithStatus(code, err.Error(), name)
	}
	if isTimeout(err) {
		return protocol.Errorf(protocol.CodeTimeout, "%v", err)
	}
	return protocol.Errorf(code, "%v", err)
}

func isTimeout(err error) bool {
	if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, os.ErrDeadlineExceeded) {
		return true
	}
	var ne net.Error
	if errors.As(err, &ne) && ne.Timeout() {
		return true
	}
	return strings.Contains(strings.ToLower(err.Error()), "timeout")
}

// statusError creates the opcua error for a bad operation status.
func statusError(what string, sc ua.StatusCode) *protocol.Error {
	name := convert.StatusName(sc)
	return protocol.WithStatus(protocol.CodeOPCUA, what+": "+name, name)
}
