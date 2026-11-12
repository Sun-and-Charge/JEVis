#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Sun and Charge Germany GmbH
#
# Cross-compiles opcua-bridge for Linux and Windows (amd64) into dist/.
# Usage: ./build.sh [version]   (default: git describe, else "dev")
set -eu
cd "$(dirname "$0")"

VERSION="${1:-$(git describe --tags --always --dirty 2>/dev/null || echo dev)}"
LDFLAGS="-s -w -X main.version=${VERSION}"
mkdir -p dist

build() {
	echo "building dist/$3 ($VERSION)"
	CGO_ENABLED=0 GOOS="$1" GOARCH="$2" go build -trimpath -ldflags "$LDFLAGS" -o "dist/$3" .
}

build linux amd64 opcua-bridge-linux-amd64
build windows amd64 opcua-bridge-windows-amd64.exe
