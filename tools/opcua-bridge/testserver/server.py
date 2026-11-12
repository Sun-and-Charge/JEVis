# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Sun and Charge Germany GmbH
"""OPC UA test server for the opcua-bridge integration test.

Test tool only, not shipped. Uses asyncua (LGPL-3.0, installed separately in a venv).

Serves opc.tcp://127.0.0.1:48400 with
- SecurityPolicy None and Basic256Sha256/SignAndEncrypt (server certificate generated at startup),
- Anonymous and UserName login (user "demo", password "demo"),
- object ns=2;s=Device1 with variables Temp (Double), Count (Int32), Enabled (Boolean), Name (String),
  Setpoint (Double, writable) and Energy (Double, historized),
- history of Energy: 97 values every 15 minutes from 2026-11-01T00:00Z to 2026-11-02T00:00Z, value i*0.25,
  at most 20 values per HistoryRead call (larger ranges need continuation points),
- processed history with the Average aggregate (i=2342), which asyncua itself does not implement.

Prints "READY <url>" on stdout when it accepts connections.
"""

import argparse
import asyncio
import logging
import socket
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

from asyncua import Server, ua
from asyncua.crypto.cert_gen import setup_self_signed_certificate
from asyncua.crypto.permission_rules import User, UserRole
from asyncua.server.history import HistoryDict, HistoryManager
from asyncua.server.user_managers import UserManager
from cryptography.x509.oid import ExtendedKeyUsageOID

NAMESPACE = "urn:jevis:opcua-bridge:testserver"
HISTORY_START = datetime(2026, 11, 1, tzinfo=timezone.utc)
HISTORY_STEP = timedelta(minutes=15)
HISTORY_COUNT = 97
MAX_VALUES_PER_CALL = 20
AVERAGE = ua.NodeId(2342, 0)


class DemoUsers(UserManager):
    """Anonymous and demo/demo are allowed (with write access); everything else is rejected."""

    def get_user(self, iserver, username=None, password=None, certificate=None):
        if username is None:
            return User(role=UserRole.Admin, name="anonymous")
        if username == "demo" and password == "demo":
            return User(role=UserRole.Admin, name="demo")
        return None


def average_history(storage: HistoryDict, node_id: ua.NodeId, details: ua.ReadProcessedDetails) -> ua.HistoryReadResult:
    result = ua.HistoryReadResult()
    if details.AggregateType != [AVERAGE]:
        result.StatusCode = ua.StatusCode(ua.StatusCodes.BadAggregateNotSupported)
        return result
    interval = timedelta(milliseconds=details.ProcessingInterval)
    if interval <= timedelta(0):
        result.StatusCode = ua.StatusCode(ua.StatusCodes.BadAggregateInvalidInputs)
        return result
    raw = storage._datachanges.get(node_id, [])
    values = []
    start = details.StartTime
    while start < details.EndTime:
        end = min(start + interval, details.EndTime)
        inside = [dv.Value.Value for dv in raw if start <= dv.SourceTimestamp < end]
        if inside:
            value = ua.Variant(sum(inside) / len(inside), ua.VariantType.Double)
            status = ua.StatusCode(ua.StatusCodes.Good)
        else:
            value = ua.Variant()
            status = ua.StatusCode(ua.StatusCodes.BadNoData)
        values.append(ua.DataValue(Value=value, StatusCode=status, SourceTimestamp=start, ServerTimestamp=start))
        start = end
    result.HistoryData = ua.HistoryData(DataValues=values)
    return result


def enable_processed_history(storage: HistoryDict) -> None:
    original = HistoryManager._read_history

    async def read_history(self, details, rv):
        if isinstance(details, ua.ReadProcessedDetails):
            return average_history(storage, rv.NodeId, details)
        return await original(self, details, rv)

    HistoryManager._read_history = read_history


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=48400)
    parser.add_argument("--exit-on-eof", action="store_true", help="stop when stdin is closed (used by the Go test)")
    args = parser.parse_args()
    logging.basicConfig(level=logging.WARNING, stream=sys.stderr)

    url = f"opc.tcp://{args.host}:{args.port}"
    server = Server(user_manager=DemoUsers())
    await server.init()
    server.set_endpoint(url)
    server.set_server_name("JEVis opcua-bridge test server")
    await server.set_application_uri("urn:jevis:opcua-bridge:testserver")
    server.set_security_policy([
        ua.SecurityPolicyType.NoSecurity,
        ua.SecurityPolicyType.Basic256Sha256_SignAndEncrypt,
    ])
    server.set_identity_tokens([ua.AnonymousIdentityToken, ua.UserNameIdentityToken])

    certdir = Path(tempfile.mkdtemp(prefix="opcua-testserver-"))
    await setup_self_signed_certificate(
        certdir / "server-key.pem",
        certdir / "server-cert.der",
        "urn:jevis:opcua-bridge:testserver",
        socket.gethostname(),
        [ExtendedKeyUsageOID.SERVER_AUTH, ExtendedKeyUsageOID.CLIENT_AUTH],
        {"commonName": "JEVis opcua-bridge test server"},
    )
    await server.load_certificate(str(certdir / "server-cert.der"))
    await server.load_private_key(str(certdir / "server-key.pem"))

    idx = await server.register_namespace(NAMESPACE)
    device = await server.nodes.objects.add_object(ua.NodeId("Device1", idx), ua.QualifiedName("Device1", idx))
    variables = [
        ("Temp", 21.5, ua.VariantType.Double),
        ("Count", 7, ua.VariantType.Int32),
        ("Enabled", True, ua.VariantType.Boolean),
        ("Name", "Boiler", ua.VariantType.String),
        ("Setpoint", 20.0, ua.VariantType.Double),
        ("Energy", 24.0, ua.VariantType.Double),
    ]
    nodes = {}
    for name, value, vtype in variables:
        node = await device.add_variable(ua.NodeId(f"Device1.{name}", idx), ua.QualifiedName(name, idx),
                                         ua.Variant(value, vtype))
        await node.set_writable()
        nodes[name] = node

    # history of Energy: pre-filled in-memory storage, small pages
    storage = HistoryDict(max_history_data_response_size=MAX_VALUES_PER_CALL)
    server.iserver.history_manager.set_storage(storage)
    energy = nodes["Energy"].nodeid
    await storage.new_historized_node(energy, None)
    for i in range(HISTORY_COUNT):
        ts = HISTORY_START + i * HISTORY_STEP
        await storage.save_node_value(energy, ua.DataValue(
            Value=ua.Variant(i * 0.25, ua.VariantType.Double), SourceTimestamp=ts, ServerTimestamp=ts))
    await nodes["Energy"].write_attribute(ua.AttributeIds.Historizing, ua.DataValue(ua.Variant(True, ua.VariantType.Boolean)))
    # ua.AccessLevel holds bit numbers, not masks
    access = sum(1 << bit for bit in (ua.AccessLevel.CurrentRead, ua.AccessLevel.CurrentWrite, ua.AccessLevel.HistoryRead))
    for attr in (ua.AttributeIds.AccessLevel, ua.AttributeIds.UserAccessLevel):
        await nodes["Energy"].write_attribute(attr, ua.DataValue(ua.Variant(access, ua.VariantType.Byte)))
    enable_processed_history(storage)

    async with server:
        print(f"READY {url}", flush=True)
        if args.exit_on_eof:
            # returns when the parent closes stdin or exits
            await asyncio.get_running_loop().run_in_executor(None, sys.stdin.read)
        else:
            while True:
                await asyncio.sleep(3600)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
