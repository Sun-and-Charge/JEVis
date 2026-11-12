// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

// Command opcua-bridge gives JEVis access to OPC UA servers. It reads JSON
// request lines on stdin and answers with JSON response lines on stdout; see
// docs/OPCUA-BRIDGE.md in the JEVis repository.
package main

import (
	"fmt"
	"log"
	"os"
	"runtime"

	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/bridge"
)

// version is set at build time with -ldflags "-X main.version=...".
var version = "dev"

func main() {
	for _, a := range os.Args[1:] {
		switch a {
		case "--version", "-version", "-v":
			fmt.Printf("opcua-bridge %s (%s, %s/%s)\n", version, runtime.Version(), runtime.GOOS, runtime.GOARCH)
			return
		case "--help", "-help", "-h":
			fmt.Println("usage: opcua-bridge [--version]\n\nServes JSON line requests on stdin until EOF or a close request; logs go to stderr.")
			return
		default:
			fmt.Fprintf(os.Stderr, "opcua-bridge: unknown argument %q\n", a)
			os.Exit(2)
		}
	}

	// stdout carries only protocol lines: keep the real stdout for the
	// protocol and send anything else printed to stdout (e.g. gopcua's codec
	// debug output) to stderr.
	protocolOut := os.Stdout
	os.Stdout = os.Stderr

	log.SetOutput(os.Stderr)
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)
	logger := log.New(os.Stderr, "opcua-bridge: ", log.LstdFlags|log.Lmicroseconds)
	logger.Printf("version %s started", version)

	b := bridge.New(bridge.GopcuaConnector{}, logger)
	if err := b.Serve(os.Stdin, protocolOut); err != nil {
		logger.Printf("stopped: %v", err)
		os.Exit(1)
	}
}
