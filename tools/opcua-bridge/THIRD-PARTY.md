# opcua-bridge: third-party software

`opcua-bridge` itself is part of JEVis and licensed under GPL-3.0-only
(Copyright (C) 2026 Sun and Charge Germany GmbH). All third-party code it uses is under permissive licenses.

## Linked into the binary

Checked with `go list -deps -f '{{if not .Standard}}{{.Module}}{{end}}' .`:

| Module | Version | License | Use |
|---|---|---|---|
| [github.com/gopcua/opcua](https://github.com/gopcua/opcua) | v0.9.1 | MIT | OPC UA client stack (packages `opcua`, `ua`, `uasc`, `uacp`, `uapolicy`, `id`, `errors`, `debug`, `stats`) |
| Go standard library | go1.25+ | BSD-3-Clause | runtime, crypto, encoding |

gopcua's MIT license notice:

```
MIT License

Copyright (c) 2018-2025 The gopcua authors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## In the module graph only (not linked)

Listed in `go.sum` because gopcua's own tests use them; none of them ends up in the binary.

| Module | Version | License |
|---|---|---|
| github.com/stretchr/testify | v1.10.0 | MIT |
| github.com/davecgh/go-spew | v1.1.1 | ISC |
| github.com/pmezard/go-difflib | v1.0.0 | BSD-3-Clause |
| gopkg.in/yaml.v3 | v3.0.1 | MIT and Apache-2.0 |

gopcua's `go.mod` also requires github.com/google/uuid (BSD-3-Clause), golang.org/x/exp, golang.org/x/term and
golang.org/x/sys (BSD-3-Clause) for its command-line tools; Go's module graph pruning leaves them out of this module.

## Test tools (not shipped)

`testserver/server.py` uses [asyncua](https://github.com/FreeOpcUa/opcua-asyncio) (LGPL-3.0-or-later) and its
dependencies (e.g. cryptography, Apache-2.0/BSD). They are installed by the developer in a separate virtual environment
for the integration test only and are neither distributed nor linked with the bridge.
