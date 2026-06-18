#!/usr/bin/env python3
"""Minimal Source RCON client.

    rcon.py <host> <port> <password> <command...>

Used by backup.sh to flush ARK's world before snapshotting, because the
hermsi/ark-server image ships arkmanager (whose rconcmd wrapper is broken by an
'Invalid steamcmd_user' config quirk) but no `rcon-cli`. The ARK server's own
RCON works fine; this talks to it directly.
"""
import socket
import struct
import sys


def rcon(host, port, password, command):
    s = socket.create_connection((host, int(port)), timeout=8)
    try:
        def send(req_id, typ, body):
            data = struct.pack("<ii", req_id, typ) + body.encode() + b"\x00\x00"
            s.sendall(struct.pack("<i", len(data)) + data)

        def recv():
            n = struct.unpack("<i", s.recv(4))[0]
            data = b""
            while len(data) < n:
                data += s.recv(n - len(data))
            return struct.unpack("<ii", data[:8])[0], data[8:-2].decode(errors="replace")

        send(1, 3, password)        # SERVERDATA_AUTH
        if recv()[0] == -1:
            sys.exit("RCON auth failed")
        send(2, 2, command)         # SERVERDATA_EXECCOMMAND
        return recv()[1]
    finally:
        s.close()


if __name__ == "__main__":
    if len(sys.argv) < 5:
        sys.exit("usage: rcon.py <host> <port> <password> <command...>")
    print(rcon(sys.argv[1], sys.argv[2], sys.argv[3], " ".join(sys.argv[4:])).strip())
