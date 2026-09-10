import socket
HOST="144.91.107.244"
ports = [50012, 50002, 50001, 50011, 15190, 443, 80, 22]
for p in ports:
    s=socket.socket(); s.settimeout(5)
    try:
        s.connect((HOST,p)); print(f"port {p:6}: OPEN")
    except Exception as e:
        print(f"port {p:6}: {type(e).__name__} {e}")
    finally:
        s.close()
# also test a definitely-up host:port to check sandbox egress on nonstd port
print("--- egress control test (electrum.blockstream.info:50002) ---")
for host,p in [("electrum.blockstream.info",50002),("github.com",443)]:
    s=socket.socket(); s.settimeout(6)
    try:
        s.connect((host,p)); print(f"{host}:{p}: OPEN")
    except Exception as e:
        print(f"{host}:{p}: {type(e).__name__} {e}")
    finally:
        s.close()
