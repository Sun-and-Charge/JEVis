# OPC UA bridge (`opcua-bridge`)

Eclipse Milo (EPL-2.0, no GPL secondary license) is not compatible with JEVis' GPL-3.0-only license. OPC UA access
therefore moves into a small separate program, `opcua-bridge`, written in Go with
[gopcua](https://github.com/gopcua/opcua) (MIT). JEVis starts it as a child process and talks to it with JSON lines
over stdin/stdout. The bridge is a separate program (an "aggregate", not a derived work), so its dependencies only
need to be license-compatible with distribution next to JEVis.

Users of OPC UA in JEVis:

- DataCollector `OPCUA-Driver` (`org.jevis.jeopc`): history import (raw and processed), browse, read, write,
  command-line client `CMDClient`.
- DataCollector `loytec-xml-dl`: OPC UA mode of the Loytec data source and the `OPCUAWriter`.
- Control Center: OPC UA browser (`plugin/object/extension/OPC`) to pick nodes for channels.

## Program

- Source: `tools/opcua-bridge/` (Go module, `go 1.25` or newer). Build: `tools/opcua-bridge/build.sh` (or
  `build.ps1`) produces `opcua-bridge-linux-amd64` and `opcua-bridge-windows-amd64.exe` in
  `tools/opcua-bridge/dist/`. Go is only needed on the build machine; on the server the Linux binary is copied to
  `bin/opcua-bridge`.
- `opcua-bridge --version` prints the version and exits. Without arguments it serves requests on stdin until EOF or
  a `close` request.
- Log output (never passwords or keys) goes to **stderr**; stdout carries only protocol lines.
- One process serves one connection (one session); the Java side starts one process per `OPCClient`.

### Locating the program (Java)

1. System property `jevis.opcua.bridge`, else
2. environment variable `JEVIS_OPCUA_BRIDGE`, else
3. `<jevis home>/bin/opcua-bridge` (`opcua-bridge.exe` on Windows), where the JEVis home is the directory of the
   running jar's parent or the working directory; the Control Center looks next to its own jar first.
   In detail (`OpcBridge.defaultCandidates`): `$JEVIS_HOME/bin` (if the variable is exported), next to the jar,
   `bin/` in the jar's directory and up to three parents (the services run from
   `<home>/<module>/target/*.jar` or `<home>/DataCollector/JEDataCollector/target/*.jar`), `bin/` in the working
   directory.

A missing program gives a clear error ("OPC UA bridge not found: …, set jevis.opcua.bridge").

The client certificate directory (`certDir` of `connect`) is the system property `jevis.opcua.certdir`, else
`<jevis home>/etc/opcua` (JEVis home: `$JEVIS_HOME`, else the parent of the `bin` directory of the program).

## Protocol

UTF-8, one JSON object per line, in both directions. Requests are answered in order, one response line per request.

Request: `{ "id": 7, "cmd": "<command>", ...parameters }`

Response: `{ "id": 7, "ok": true, "result": <value> }` or
`{ "id": 7, "ok": false, "error": { "code": "<code>", "message": "<text>", "status": "<OPC UA status name or null>" } }`

Error codes: `bad_request` (unknown command or parameter), `not_connected`, `connect_failed`, `timeout`,
`opcua` (service fault or bad status from the server; `status` holds e.g. `BadNodeIdUnknown`), `internal`.
Parameter names are case-sensitive.

Every request may carry `timeoutMs` (optional); without it the `timeoutMs` of `connect` applies (default 30000).

Common formats:

- **NodeId**: the OPC UA string form, e.g. `ns=2;s=Device1.Temp`, `i=84`, `ns=1;g=…`, `ns=3;b=…`.
- **Timestamps**: ISO 8601 UTC with milliseconds, e.g. `2026-11-12T08:15:00.000Z`. Requests accept any ISO 8601
  offset.
- **Values**: JSON number, string, boolean, or `null` (no value / bad quality). Arrays as JSON arrays. Date values as
  timestamp strings, ByteString as base64, LocalizedText as its text, QualifiedName as `ns:name`. NaN/Infinity,
  structures (ExtensionObject) and DiagnosticInfo are `null`; StatusCode values are numbers.
- **Status**: the status code name (`Good`, `GoodNoData`, `BadNoData`, `UncertainLastUsableValue`, …) plus the
  numeric code as `statusCode`. The name ignores the info bits (lower 16 bits); `statusCode` keeps them.

### `endpoints`

`{ "cmd": "endpoints", "url": "opc.tcp://host:4840" }` →

```json
[ { "url": "opc.tcp://host:4840", "securityPolicyUri": "http://opcfoundation.org/UA/SecurityPolicy#None",
    "securityPolicy": "None", "securityMode": "None", "securityLevel": 0,
    "userTokens": ["Anonymous", "UserName"], "serverCertificate": null } ]
```

`securityPolicy` short names: `None`, `Basic128Rsa15`, `Basic256`, `Basic256Sha256`, `Aes128_Sha256_RsaOaep`,
`Aes256_Sha256_RsaPss`. `securityMode`: `None`, `Sign`, `SignAndEncrypt`. `serverCertificate`: base64 DER or null.

### `connect`

```json
{ "cmd": "connect", "url": "opc.tcp://host:4840",
  "securityPolicy": "auto", "securityMode": "auto",
  "user": null, "password": null,
  "certFile": null, "keyFile": null, "certDir": "/home/jevis/jevis/etc/opcua",
  "applicationName": "JEVis DataCollector", "applicationUri": "urn:jevis:datacollector",
  "trustServerCertificate": true, "timeoutMs": 30000 }
```

- `securityPolicy`/`securityMode` `"auto"`: pick the endpoint like Milo's `autoSelectEndpoint` did: the highest
  `securityLevel` among endpoints whose user token types allow the given credentials (UserName if `user` is set,
  else Anonymous); explicit values select that endpoint.
- Security other than `None` needs a client certificate: `certFile`/`keyFile` (PEM or DER) if given, else the bridge
  creates a self-signed RSA-2048 certificate with the `applicationUri` once and keeps it in `certDir`
  (`opcua-bridge-cert.der`, `opcua-bridge-key.pem`, file mode 0600 for the key).
- `trustServerCertificate: true` accepts any server certificate (as the old `InsecureValidator`); `false` checks it
  against `certDir/trusted/*.der`.
- Result: `{ "endpoint": { …as in endpoints… }, "sessionTimeoutMs": 1200000 }` (the server's revised timeout; the
  bridge requests 20 min).
- Defaults of omitted/`null` parameters: `securityPolicy`/`securityMode` `auto`, `applicationName` `JEVis`,
  `applicationUri` `urn:jevis:opcua-bridge`, `certDir` `~/.jevis/opcua`, `trustServerCertificate` `true`.
- An existing certificate in `certDir` is reused as is, even if its application URI differs from `applicationUri`
  (the server sees the certificate's URI).
- The trust check applies to endpoints with security only. An untrusted certificate fails with `connect_failed`,
  status `BadCertificateUntrusted`, and is saved as `certDir/rejected/<sha1 thumbprint>.der`; moving it to
  `certDir/trusted/` trusts it. Only exact certificate matches count (no CA chains).
- The channel is opened to the requested `url` (not the endpoint's URL, which may name a host that is not resolvable
  from JEVis). `connect` on a connected bridge closes the old session first. A lost connection is re-established
  by the bridge (gopcua auto-reconnect); requests meanwhile fail.

### `browse`

`{ "cmd": "browse", "nodeId": "i=85", "nodeClasses": ["Object", "Variable"], "references": "Hierarchical" }` →

```json
[ { "nodeId": "ns=2;s=Device1", "browseName": "2:Device1", "displayName": "Device1",
    "nodeClass": "Object", "typeDefinition": "i=58", "referenceType": "i=35" } ]
```

`nodeClasses` (default all) filters by `Object`, `Variable`, `Method`, `ObjectType`, `VariableType`,
`ReferenceType`, `DataType`, `View`. `references`: `Hierarchical` (default, `i=33` incl. subtypes, forward) or
`All` (`i=31` incl. subtypes, forward). `nodeId` defaults to `i=85`. Continuation points are followed inside the
bridge; the result is the complete list. `typeDefinition` is `null` when the target has none; targets with a
namespace URI or server index are written `nsu=<uri>;…` / `svr=<n>;…`.

### `read`

`{ "cmd": "read", "nodeIds": ["ns=2;s=Device1.Temp"], "attribute": "Value" }` →

```json
[ { "nodeId": "ns=2;s=Device1.Temp", "value": 21.5, "dataType": "Double",
    "sourceTimestamp": "2026-11-12T08:15:00.000Z", "serverTimestamp": "2026-11-12T08:15:00.120Z",
    "status": "Good", "statusCode": 0 } ]
```

`attribute`: `Value` (default), `DataType` (`value` = data type name such as `Double`, or the NodeId string for
non-standard types), `DisplayName`, `BrowseName`, `Description`, `NodeClass`, `AccessLevel`, `Historizing`.
`NodeClass` gives the name (`Variable`, …), `AccessLevel` the number, `Historizing` a boolean. Per-node bad statuses
(e.g. `BadNodeIdUnknown`) are returned in the entry (`value` `null`), not as error. `dataType` is the built-in type
of the returned value (`null` when there is no value).

### `write`

`{ "cmd": "write", "nodeId": "ns=2;s=Device1.Setpoint", "value": 21.0, "dataType": "Double" }` →
`{ "status": "Good", "statusCode": 0 }` (a bad status is returned as error `opcua`).

`dataType`: `Boolean`, `SByte`, `Byte`, `Int16`, `UInt16`, `Int32`, `UInt32`, `Int64`, `UInt64`, `Float`, `Double`,
`String`, `DateTime`; or `auto` (default; read the node's DataType first; standard subtypes map to their built-in
type, e.g. `Duration` → `Double`, `UtcTime` → `DateTime`, `Enumeration` → `Int32`; other types need an explicit
`dataType`). Numbers may also be given as strings; a JSON array writes an array. Values that do not fit the type
give `bad_request`.

### `history-raw`

```json
{ "cmd": "history-raw", "nodeId": "ns=2;s=Device1.Energy",
  "from": "2026-11-01T00:00:00Z", "to": "2026-11-12T00:00:00Z",
  "maxValues": 0, "returnBounds": true, "timestamps": "Source" }
```

→ `{ "values": [ { "ts": "…", "value": 123.4, "status": "Good", "statusCode": 0 } ], "truncated": false }`

`ReadRawModifiedDetails` (isReadModified false, `numValuesPerNode` 0 = server default) with `TimestampsToReturn`
`Source` (default) or `Server`/`Both`. Continuation points are followed until done; `maxValues` > 0 caps the total
(then `truncated: true` and the continuation point is released). Values are in server order.

Optional: `valuesPerCall` (default 0) sets `numValuesPerNode` per HistoryRead call; `returnBounds` defaults to
`true`. `ts` is the source timestamp (`Server`: the server timestamp; falls back to the server timestamp when the
source timestamp is missing). A node result `GoodNoData` or `BadNoData` gives an empty `values` list; other bad
results give error `opcua`.

### `history-processed`

```json
{ "cmd": "history-processed", "nodeId": "ns=2;s=Device1.Energy",
  "from": "…", "to": "…", "aggregate": "i=2342", "intervalMs": 900000,
  "useServerCapabilitiesDefaults": true, "treatUncertainAsBad": false,
  "percentDataBad": 0, "percentDataGood": 0, "useSlopedExtrapolation": false }
```

→ same shape as `history-raw`. `aggregate` is the aggregate function NodeId (the channel's "function node"); the
configuration defaults match the old Milo call `AggregateConfiguration(true, false, 0, 0, false)`. `intervalMs` is
required; `maxValues` and `timestamps` work as for `history-raw`; per-interval statuses (e.g. `BadNoData`) are
returned in the values.

### `close`

`{ "cmd": "close" }` → `{}`; closes the session and exits. EOF on stdin does the same.

## Java side

Package `org.jevis.jeopc` (OPCUA-Driver) gets Milo-free types used by the driver, Loytec and the Control Center:
`OpcBridge` (process, request/response, timeouts, restart on crash), `OpcEndpoint`, `OpcReference`, `OpcValue`,
`OpcException`. `OPCClient` keeps its role and public methods, with these types instead of Milo's. Milo is removed
from all `pom.xml` files.

- NodeIds are plain strings in the string form above.
- Each request has a timeout on the Java side (connect: connection timeout of the data source + 5 s; browse, read,
  write: its read timeout; history: ten times the read timeout, at least 10 minutes). On a timeout or when the
  process ends, the Java side stops the process; the next request starts a new one and `OPCClient` sends
  `connect` again before it. Late responses with an older `id` are ignored.
- `OpcException` codes are the error codes above plus `bridge` (Java side only: program not found, could not be
  started, ended unexpectedly, or already closed).
- `OPCClient.autoSelectEndpoint()` keeps the old JEVis rule (the endpoint with security `None` for the configured
  URL, else the first one) and the driver, Loytec and the Control Center connect with that endpoint. `connect`
  with `"auto"` is used only when no endpoint was set.
- History: a channel with a "Function Node ID" uses `history-processed` (aggregate = that node, interval = the
  "Function Interval" in ms), else `history-raw`. An `opcua` error (bad status such as `BadNoData`) gives an
  empty result for that period, as before; other errors abort the channel's readout.

## Tests

- Go: unit tests for parsing/formatting, endpoint selection, value conversion; an integration test (build tag
  `integration`) against a local test server with history.
- Java: unit tests with a fake bridge (a small script answering canned lines); no network.
- Integration (developer machine): Python `asyncua` test server with history (only a test tool, not shipped), driven
  by `tools/opcua-bridge/testserver/`.
- Real device check (Loytec with history) by the user before deployment.
