#!/usr/bin/env python3
"""Local TLS 1.2 servers for tests/dex/TlsTest.java (see run_tls_test.sh), one port per certificate:

  47441 good (leaf + intermediate, ALPN http/1.1)   47442 expired   47443 wronghost   47444 selfsigned

Each connection: "GET ..." gets a small HTTP/1.1 response and is closed; "ECHO\\n" echoes every byte until the
client stops sending, then closes. One cipher suite is offered so that every client negotiates the same one.
"""
import os
import socket
import ssl
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
PORTS = {47441: "good", 47442: "expired", 47443: "wronghost", 47444: "selfsigned"}
BODY = b"hello over tls\n"


def context(name):
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.maximum_version = ssl.TLSVersion.TLSv1_2
    ctx.set_ciphers("ECDHE-RSA-AES128-GCM-SHA256")
    ctx.set_alpn_protocols(["http/1.1"])
    ctx.load_cert_chain(os.path.join(HERE, name + ".pem"), os.path.join(HERE, name + ".key"))
    return ctx


def serve_conn(conn):
    try:
        head = b""
        while b"\n" not in head and len(head) < 8192:
            chunk = conn.recv(4096)
            if not chunk:
                return
            head += chunk
        if head.startswith(b"GET "):
            while b"\r\n\r\n" not in head:
                chunk = conn.recv(4096)
                if not chunk:
                    return
                head += chunk
            conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n"
                         b"Connection: close\r\n\r\n" % len(BODY) + BODY)
        elif head.startswith(b"ECHO\n"):
            rest = head[5:]
            if rest:
                conn.sendall(rest)
            while True:
                chunk = conn.recv(65536)
                if not chunk:
                    break
                conn.sendall(chunk)
        try:
            conn.unwrap()
        except (ssl.SSLError, OSError):
            pass
    except (ssl.SSLError, OSError):
        pass
    finally:
        conn.close()


def serve(port, name):
    ctx = context(name)
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", port))
    s.listen(16)
    while True:
        raw, _ = s.accept()
        try:
            conn = ctx.wrap_socket(raw, server_side=True)
        except (ssl.SSLError, OSError):
            raw.close()
            continue
        threading.Thread(target=serve_conn, args=(conn,), daemon=True).start()


def main():
    for port, name in PORTS.items():
        threading.Thread(target=serve, args=(port, name), daemon=True).start()
    print("READY", flush=True)
    sys.stdin.read()  # runs until the runner closes our stdin


if __name__ == "__main__":
    main()
