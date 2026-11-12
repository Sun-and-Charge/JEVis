# OPC UA test server for the bridge integration test

`server.py` is a small OPC UA server built with [asyncua](https://github.com/FreeOpcUa/opcua-asyncio)
(LGPL-3.0-or-later). It is a developer test tool only: it is not shipped and not linked with the bridge.

It serves `opc.tcp://127.0.0.1:48400` with

- endpoints SecurityPolicy `None` and `Basic256Sha256`/`SignAndEncrypt` (server certificate generated at startup in a
  temporary directory),
- user tokens `Anonymous` and `UserName` (user `demo`, password `demo`),
- object `Device1` in namespace `urn:jevis:opcua-bridge:testserver` (index 1 with asyncua 2.x; the test reads it from
  the NamespaceArray) with the variables `Device1.Temp` (Double), `Device1.Count` (Int32), `Device1.Enabled`
  (Boolean), `Device1.Name` (String), `Device1.Setpoint` (Double) and `Device1.Energy` (Double, historized), all
  writable,
- raw history of `Device1.Energy`: 97 values every 15 minutes from `2026-11-01T00:00:00Z` to `2026-11-02T00:00:00Z`,
  value `i * 0.25`; at most 20 values per HistoryRead call, so longer ranges need continuation points,
- processed history with the `Average` aggregate (`i=2342`). asyncua does not implement processed history
  (`BadNotImplemented`); `server.py` adds a simple Average (interval start as timestamp, `BadNoData` for empty
  intervals, `BadAggregateNotSupported` for other aggregates).

## Setup

Python 3.10 or newer. Create a virtual environment outside the repository, e.g.

```sh
python -m venv ~/opcua-venv
~/opcua-venv/bin/python -m pip install -r tools/opcua-bridge/testserver/requirements.txt
```

(Windows: `opcua-venv\Scripts\python.exe` instead of `opcua-venv/bin/python`.)

## Run

Start the server by hand:

```sh
~/opcua-venv/bin/python -I tools/opcua-bridge/testserver/server.py   # prints "READY opc.tcp://127.0.0.1:48400"
```

Options: `--host`, `--port`, `--exit-on-eof` (stop when stdin is closed).

## Integration test

From `tools/opcua-bridge`:

```sh
# server already running:
go test -tags integration ./integration/
# or let the test start and stop it:
OPCUA_TEST_PYTHON=~/opcua-venv/bin/python go test -tags integration ./integration/
```

The test builds the bridge, starts it as a child process and drives every command through stdin/stdout: endpoints,
connect (None/Anonymous, UserName with auto selection, UserName on None, Basic256Sha256/SignAndEncrypt with a
generated client certificate and with `certFile`/`keyFile`, untrusted and trusted server certificate), browse, read,
write (Double, Int32, Boolean, String, `auto`), history-raw (with continuation points, `maxValues`, offsets, empty
range), history-processed (Average per hour) and close/EOF.
