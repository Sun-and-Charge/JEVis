# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Sun and Charge Germany GmbH
#
# Cross-compiles opcua-bridge for Linux and Windows (amd64) into dist\.
# Usage: .\build.ps1 [-Version <version>]   (default: git describe, else "dev")
param([string]$Version = "")
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

if (-not $Version) {
    $Version = (git describe --tags --always --dirty 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $Version) { $Version = "dev" }
}
$ldflags = "-s -w -X main.version=$Version"
New-Item -ItemType Directory -Force dist | Out-Null

$saved = @{ CGO_ENABLED = $env:CGO_ENABLED; GOOS = $env:GOOS; GOARCH = $env:GOARCH }
try {
    foreach ($t in @(@("linux", "amd64", "opcua-bridge-linux-amd64"), @("windows", "amd64", "opcua-bridge-windows-amd64.exe"))) {
        Write-Host "building dist/$($t[2]) ($Version)"
        $env:CGO_ENABLED = "0"; $env:GOOS = $t[0]; $env:GOARCH = $t[1]
        go build -trimpath -ldflags $ldflags -o "dist/$($t[2])" .
        if ($LASTEXITCODE -ne 0) { throw "go build failed for $($t[0])/$($t[1])" }
    }
} finally {
    foreach ($k in $saved.Keys) {
        if ($null -eq $saved[$k]) { Remove-Item "env:$k" -ErrorAction SilentlyContinue } else { Set-Item "env:$k" $saved[$k] }
    }
}
