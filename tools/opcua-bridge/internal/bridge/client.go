// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package bridge

import (
	"context"
	"crypto/rsa"
	"errors"
	"fmt"
	"time"

	"github.com/gopcua/opcua"
	"github.com/gopcua/opcua/ua"
)

// SessionTimeout is the requested OPC UA session timeout.
const SessionTimeout = 20 * time.Minute

// ConnectConfig holds everything needed to open a session on a selected endpoint.
type ConnectConfig struct {
	User            string
	Password        string
	Certificate     []byte          // DER, nil for SecurityPolicy None
	PrivateKey      *rsa.PrivateKey // nil for SecurityPolicy None
	ApplicationName string
	ApplicationURI  string
	Timeout         time.Duration
}

// Connector discovers endpoints and opens sessions.
type Connector interface {
	Endpoints(ctx context.Context, url string) ([]*ua.EndpointDescription, error)
	Connect(ctx context.Context, url string, ep *ua.EndpointDescription, cfg ConnectConfig) (Client, error)
}

// Client is an open OPC UA session. The bridge only uses these services, so
// tests can replace the session with a fake.
type Client interface {
	Read(ctx context.Context, req *ua.ReadRequest) (*ua.ReadResponse, error)
	Write(ctx context.Context, req *ua.WriteRequest) (*ua.WriteResponse, error)
	Browse(ctx context.Context, req *ua.BrowseRequest) (*ua.BrowseResponse, error)
	BrowseNext(ctx context.Context, req *ua.BrowseNextRequest) (*ua.BrowseNextResponse, error)
	HistoryRead(ctx context.Context, req *ua.HistoryReadRequest) (*ua.HistoryReadResponse, error)
	SessionTimeout() time.Duration
	Close(ctx context.Context) error
}

// GopcuaConnector is the Connector based on github.com/gopcua/opcua.
type GopcuaConnector struct{}

// Endpoints calls GetEndpoints on the server.
func (GopcuaConnector) Endpoints(ctx context.Context, url string) ([]*ua.EndpointDescription, error) {
	return opcua.GetEndpoints(ctx, url)
}

// Connect opens a secure channel and session. The channel is opened to the
// given url (not the endpoint's URL, which may contain a host name that is not
// resolvable from here); security settings come from the endpoint.
func (GopcuaConnector) Connect(ctx context.Context, url string, ep *ua.EndpointDescription, cfg ConnectConfig) (Client, error) {
	opts := []opcua.Option{
		opcua.ApplicationName(cfg.ApplicationName),
		opcua.ApplicationURI(cfg.ApplicationURI),
		opcua.ProductURI("urn:jevis:opcua-bridge"),
		opcua.SessionName("JEVis opcua-bridge"),
		opcua.SessionTimeout(SessionTimeout),
		opcua.RequestTimeout(cfg.Timeout),
		opcua.DialTimeout(cfg.Timeout),
		opcua.AutoReconnect(true),
	}
	tokenType := ua.UserTokenTypeAnonymous
	if cfg.User != "" {
		tokenType = ua.UserTokenTypeUserName
		opts = append(opts, opcua.AuthUsername(cfg.User, cfg.Password))
	} else {
		opts = append(opts, opcua.AuthAnonymous())
	}
	opts = append(opts, opcua.SecurityFromEndpoint(ep, tokenType))
	if cfg.Certificate != nil {
		opts = append(opts, opcua.Certificate(cfg.Certificate), opcua.PrivateKey(cfg.PrivateKey))
	}
	c, err := opcua.NewClient(url, opts...)
	if err != nil {
		return nil, err
	}
	if err := c.Connect(ctx); err != nil {
		return nil, err
	}
	return &gopcuaClient{c: c}, nil
}

type gopcuaClient struct {
	c *opcua.Client
}

func (g *gopcuaClient) Read(ctx context.Context, req *ua.ReadRequest) (*ua.ReadResponse, error) {
	return g.c.Read(ctx, req)
}

func (g *gopcuaClient) Write(ctx context.Context, req *ua.WriteRequest) (*ua.WriteResponse, error) {
	return g.c.Write(ctx, req)
}

func (g *gopcuaClient) Browse(ctx context.Context, req *ua.BrowseRequest) (*ua.BrowseResponse, error) {
	return g.c.Browse(ctx, req)
}

func (g *gopcuaClient) BrowseNext(ctx context.Context, req *ua.BrowseNextRequest) (*ua.BrowseNextResponse, error) {
	return g.c.BrowseNext(ctx, req)
}

// HistoryRead sends the request as is; gopcua's HistoryReadRawModified always
// asks for both timestamps and cannot release continuation points.
func (g *gopcuaClient) HistoryRead(ctx context.Context, req *ua.HistoryReadRequest) (*ua.HistoryReadResponse, error) {
	var res *ua.HistoryReadResponse
	err := g.c.Send(ctx, req, func(v ua.Response) error {
		r, ok := v.(*ua.HistoryReadResponse)
		if !ok {
			return fmt.Errorf("invalid response: got %T want *ua.HistoryReadResponse", v)
		}
		res = r
		return nil
	})
	if err == nil && res == nil {
		err = errors.New("empty HistoryRead response")
	}
	return res, err
}

func (g *gopcuaClient) SessionTimeout() time.Duration {
	if s := g.c.Session(); s != nil {
		return s.RevisedTimeout()
	}
	return 0
}

func (g *gopcuaClient) Close(ctx context.Context) error {
	return g.c.Close(ctx)
}
