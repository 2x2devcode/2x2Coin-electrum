import socket, ssl, json, sys

HOST, PORT = "144.91.107.244", 50012
TIMEOUT = 12

def send_reqs(sock):
    reqs = [
        {"id":0,"method":"server.version","params":["2x2probe","1.4"]},
        {"id":1,"method":"server.banner","params":[]},
        {"id":2,"method":"blockchain.headers.subscribe","params":[]},
    ]
    payload = "".join(json.dumps(r)+"\n" for r in reqs).encode()
    sock.sendall(payload)
    sock.settimeout(TIMEOUT)
    buf = b""
    try:
        while buf.count(b"\n") < 3:
            chunk = sock.recv(4096)
            if not chunk: break
            buf += chunk
    except socket.timeout:
        pass
    return buf.decode(errors="replace")

def try_ssl():
    print("=== Trying SSL/TLS on %s:%s ===" % (HOST,PORT))
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    raw = socket.create_connection((HOST,PORT), timeout=TIMEOUT)
    s = ctx.wrap_socket(raw, server_hostname=HOST)
    print("TLS handshake OK, cipher:", s.cipher())
    out = send_reqs(s)
    s.close()
    return out

def try_plain():
    print("=== Trying plain TCP on %s:%s ===" % (HOST,PORT))
    s = socket.create_connection((HOST,PORT), timeout=TIMEOUT)
    out = send_reqs(s)
    s.close()
    return out

for fn in (try_ssl, try_plain):
    try:
        out = fn()
        print("--- RESPONSE ---")
        for line in out.splitlines():
            print(line[:400])
        if out.strip():
            print("=== SUCCESS via", fn.__name__, "===")
            break
    except Exception as e:
        print("FAILED:", type(e).__name__, e)
    print()
