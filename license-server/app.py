"""Device-key-bound activation service. Runtime keys and databases live outside source."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import threading
import time
import uuid

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

PACKAGE = "com.shihab.diplay.preface"
SIGNER = "db0dc2a34dc06f22db7a3d10103fa011167712ebe61985ca2103084ff542cb82"
LEASE_SECONDS = 300
HERE = Path(__file__).resolve().parent


def sha(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def b64(value: bytes) -> str:
    return base64.b64encode(value).decode("ascii")


def unb64(value: str) -> bytes:
    try:
        return base64.b64decode(value, validate=True)
    except (ValueError, TypeError) as error:
        raise ApiError(400, "invalid_encoding") from error


def canonical(action, challenge_id, nonce, device, public_key, code, package, signer):
    return "\n".join(("DP-LIC1", action, challenge_id, nonce, device, sha(public_key),
                      sha(code.encode("ascii")) if code else "", package, signer)).encode("ascii")


def normalize_code(value):
    value = value.replace("-", "").replace(" ", "").upper()
    if not re.fullmatch(r"[0-9A-F]{32}", value):
        raise ApiError(403, "activation_rejected")
    return value


class ApiError(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code
        super().__init__(code)


def initialize(directory: Path, package=PACKAGE, signer=SIGNER):
    directory.mkdir(parents=True, exist_ok=True)
    if any(directory.iterdir()):
        raise ValueError("Use an empty data directory; existing signing keys must never be replaced.")
    if not re.fullmatch(r"[a-zA-Z0-9_.]{3,150}", package) or not re.fullmatch(r"[0-9a-f]{64}", signer):
        raise ValueError("Invalid package or signer")
    private = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    token = secrets.token_urlsafe(36)
    files = {
        "signing-private.pem": private.private_bytes(serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8, serialization.NoEncryption()),
        "signing-public.der": private.public_key().public_bytes(serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo),
        "admin-token.txt": token.encode("ascii"),
        "config.json": json.dumps({"package": package, "signer": signer,
            "adminTokenHash": sha(token.encode("ascii"))}).encode("utf8"),
    }
    for name, data in files.items():
        path = directory / name
        with path.open("xb") as output:
            output.write(data)
        path.chmod(0o600)
    directory.chmod(0o700)
    Service(directory)


class Service:
    def __init__(self, directory: Path, clock=time.time):
        self.directory, self.clock = Path(directory), clock
        self.config = json.loads((self.directory / "config.json").read_text())
        self.key = serialization.load_pem_private_key(
            (self.directory / "signing-private.pem").read_bytes(), password=None)
        self.rate_lock, self.rates = threading.Lock(), {}
        with self.db() as connection:
            connection.executescript("""
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS licenses (
                    id TEXT PRIMARY KEY, code_hash TEXT UNIQUE NOT NULL,
                    label TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
                    valid_until INTEGER NOT NULL, device TEXT UNIQUE,
                    created_at INTEGER NOT NULL, activated_at INTEGER, last_seen INTEGER);
                CREATE TABLE IF NOT EXISTS challenges (
                    id TEXT PRIMARY KEY, nonce TEXT NOT NULL, expires INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY, at INTEGER NOT NULL, action TEXT NOT NULL,
                    license_id TEXT NOT NULL);
            """)

    @contextmanager
    def db(self):
        connection = sqlite3.connect(self.directory / "licenses.sqlite3", timeout=10)
        connection.row_factory = sqlite3.Row
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def limited(self, address, bucket, limit):
        now = int(self.clock()) // 60
        with self.rate_lock:
            self.rates = {k: v for k, v in self.rates.items() if k[0] >= now - 1}
            key = (now, address, bucket)
            self.rates[key] = self.rates.get(key, 0) + 1
            if self.rates[key] > limit:
                raise ApiError(429, "rate_limited")

    def challenge(self):
        now = int(self.clock())
        identifier, nonce = secrets.token_hex(24), secrets.token_hex(32)
        with self.db() as connection:
            connection.execute("BEGIN IMMEDIATE")
            connection.execute("DELETE FROM challenges WHERE expires <= ?", (now,))
            if connection.execute("SELECT count(*) FROM challenges").fetchone()[0] >= 10000:
                raise ApiError(429, "rate_limited")
            connection.execute("INSERT INTO challenges VALUES (?,?,?)", (identifier, nonce, now + 120))
        return {"id": identifier, "nonce": nonce}

    def authorize(self, action, request):
        now = int(self.clock())
        try:
            cid, device, package, signer = (request[k] for k in ("challengeId", "deviceId", "package", "signer"))
            public_bytes, signature = unb64(request["publicKey"]), unb64(request["signature"])
            code = normalize_code(request["code"]) if action == "activate" else ""
            if (not re.fullmatch(r"[0-9a-f]{48}", cid) or sha(public_bytes) != device or
                    package != self.config["package"] or signer != self.config["signer"]):
                raise ApiError(403, "activation_rejected")
            public_key = serialization.load_der_public_key(public_bytes)
            if not isinstance(public_key, rsa.RSAPublicKey) or public_key.key_size != 2048:
                raise ApiError(403, "activation_rejected")
        except (KeyError, ValueError, TypeError, AttributeError) as error:
            raise ApiError(400, "invalid_request") from error
        # Consume each nonce before verification. It cannot authorize a replay.
        with self.db() as connection:
            connection.execute("BEGIN IMMEDIATE")
            challenge = connection.execute("SELECT * FROM challenges WHERE id=?", (cid,)).fetchone()
            connection.execute("DELETE FROM challenges WHERE id=?", (cid,))
        if challenge is None or challenge["expires"] <= now:
            raise ApiError(403, "challenge_expired")
        try:
            public_key.verify(signature, canonical(action, cid, challenge["nonce"], device,
                public_bytes, code, package, signer), padding.PKCS1v15(), hashes.SHA256())
        except InvalidSignature as error:
            raise ApiError(403, "invalid_device_proof") from error
        with self.db() as connection:
            connection.execute("BEGIN IMMEDIATE")
            if action == "activate":
                row = connection.execute("SELECT * FROM licenses WHERE code_hash=?", (sha(code.encode()),)).fetchone()
            else:
                row = connection.execute("SELECT * FROM licenses WHERE device=?", (device,)).fetchone()
            if row is None or not row["enabled"] or row["valid_until"] <= now:
                raise ApiError(403, "license_unavailable")
            if row["device"] is not None and row["device"] != device:
                raise ApiError(403, "device_already_bound")
            if row["device"] is None:
                if connection.execute("SELECT 1 FROM licenses WHERE device=?", (device,)).fetchone():
                    raise ApiError(409, "device_has_license")
                connection.execute("UPDATE licenses SET device=?,activated_at=? WHERE id=?", (device, now, row["id"]))
                connection.execute("INSERT INTO events(at,action,license_id) VALUES (?,?,?)", (now, "activate", row["id"]))
            connection.execute("UPDATE licenses SET last_seen=? WHERE id=?", (now, row["id"]))
            expiry = min(now + LEASE_SECONDS, row["valid_until"])
            payload = "\n".join(("DP1", row["id"], device, str(now), str(expiry), package, signer, cid)).encode("ascii")
            signed = self.key.sign(payload, padding.PKCS1v15(), hashes.SHA256())
        return {"payload": b64(payload), "signature": b64(signed)}

    def admin(self, authorization):
        raw = authorization.removeprefix("Bearer ") if authorization.startswith("Bearer ") else ""
        if not raw or not hmac.compare_digest(sha(raw.encode()), self.config["adminTokenHash"]):
            raise ApiError(401, "unauthorized")

    def issue(self, data):
        label, days = data.get("label", ""), data.get("days", 365)
        if not isinstance(label, str) or len(label) > 100 or type(days) is not int or not 1 <= days <= 3650:
            raise ApiError(400, "invalid_license")
        code, identifier, now = secrets.token_hex(16).upper(), str(uuid.uuid4()), int(self.clock())
        with self.db() as connection:
            connection.execute("INSERT INTO licenses(id,code_hash,label,valid_until,created_at) VALUES (?,?,?,?,?)",
                (identifier, sha(code.encode()), label, now + days * 86400, now))
            connection.execute("INSERT INTO events(at,action,license_id) VALUES (?,?,?)", (now, "issue", identifier))
        return {"id": identifier, "code": "-".join(code[i:i+4] for i in range(0, 32, 4))}

    def set_enabled(self, data):
        identifier, enabled = data.get("id"), data.get("enabled")
        if not isinstance(identifier, str) or type(enabled) is not bool:
            raise ApiError(400, "invalid_request")
        with self.db() as connection:
            if connection.execute("UPDATE licenses SET enabled=? WHERE id=?", (int(enabled), identifier)).rowcount != 1:
                raise ApiError(404, "not_found")
            connection.execute("INSERT INTO events(at,action,license_id) VALUES (?,?,?)",
                (int(self.clock()), "enable" if enabled else "disable", identifier))
        return {"ok": True}

    def listing(self):
        with self.db() as connection:
            rows = connection.execute("SELECT id,label,enabled,valid_until,device,created_at,activated_at,last_seen FROM licenses ORDER BY created_at DESC LIMIT 1000")
            return {"licenses": [dict(row) for row in rows]}

    def __call__(self, environ, start_response):
        headers = [("Cache-Control", "no-store"), ("X-Content-Type-Options", "nosniff"),
                   ("X-Frame-Options", "DENY"), ("Referrer-Policy", "no-referrer"),
                   ("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")]
        status, content_type = 200, "application/json; charset=utf-8"
        try:
            path, method = environ.get("PATH_INFO", ""), environ.get("REQUEST_METHOD", "GET")
            address = environ.get("REMOTE_ADDR", "unknown")  # Never trust an arbitrary X-Forwarded-For.
            if method == "GET" and path in ("/", "/admin.js", "/style.css", "/favicon.svg"):
                name, content_type = {"/": ("index.html", "text/html; charset=utf-8"),
                    "/admin.js": ("admin.js", "text/javascript; charset=utf-8"),
                    "/style.css": ("style.css", "text/css; charset=utf-8"),
                    "/favicon.svg": ("favicon.svg", "image/svg+xml")}[path]
                body = (HERE / "static" / name).read_bytes()
            else:
                if method != "POST":
                    raise ApiError(405, "post_required")
                length = int(environ.get("CONTENT_LENGTH") or 0)
                if not 0 < length <= 16384 or not environ.get("CONTENT_TYPE", "").startswith("application/json"):
                    raise ApiError(400, "invalid_request")
                try:
                    request = json.loads(environ["wsgi.input"].read(length))
                except (ValueError, UnicodeError) as error:
                    raise ApiError(400, "invalid_json") from error
                if not isinstance(request, dict):
                    raise ApiError(400, "invalid_request")
                if path.startswith("/admin/"):
                    self.limited(address, "admin", 60)
                    self.admin(environ.get("HTTP_AUTHORIZATION", ""))
                    handlers = {"/admin/list": lambda: self.listing(), "/admin/issue": lambda: self.issue(request),
                                "/admin/state": lambda: self.set_enabled(request)}
                else:
                    self.limited(address, "client", 240)
                    handlers = {"/v1/challenge": self.challenge,
                                "/v1/activate": lambda: self.authorize("activate", request),
                                "/v1/refresh": lambda: self.authorize("refresh", request)}
                if path not in handlers:
                    raise ApiError(404, "not_found")
                body = json.dumps(handlers[path](), ensure_ascii=False).encode("utf8")
        except ApiError as error:
            status, body = error.status, json.dumps({"error": error.code}).encode()
        except (ValueError, TypeError):
            status, body = 400, b'{"error":"invalid_request"}'
        except Exception:
            # No request bodies, activation codes, admin credentials, or private keys in logs.
            status, body = 500, b'{"error":"internal_error"}'
        names = {200: "OK", 400: "Bad Request", 401: "Unauthorized", 403: "Forbidden",
                 404: "Not Found", 405: "Method Not Allowed", 409: "Conflict", 429: "Too Many Requests", 500: "Internal Server Error"}
        start_response(f"{status} {names[status]}", headers + [("Content-Type", content_type), ("Content-Length", str(len(body)))])
        return [body]


_service = None


def application(environ, start_response):
    global _service
    if _service is None:
        _service = Service(Path(os.environ["DIPLAY_LICENSE_DATA"]))
    return _service(environ, start_response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["init", "serve", "export-client"])
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--url", default="")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    if args.action == "init":
        initialize(args.data)
        print("Initialized. Keep the data directory private and back it up. Admin credential: admin-token.txt")
    elif args.action == "export-client":
        from urllib.parse import urlsplit
        if not args.out:
            parser.error("--out is required")
        parsed = urlsplit(args.url)
        if args.url and (parsed.scheme != "https" or not parsed.hostname or parsed.path not in ("", "/") or parsed.query or parsed.fragment or parsed.username or parsed.password):
            parser.error("Production URL must be an HTTPS origin, without credentials, query or path")
        config = json.loads((args.data / "config.json").read_text())
        target = args.out / "license/server.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({"url": args.url.rstrip("/"), "publicKey": b64((args.data / "signing-public.der").read_bytes()),
            "package": config["package"], "signer": config["signer"], "contact": "starts181004"}, indent=2), encoding="utf8")
        print("Public client configuration exported; empty URL is an activation-screen preview only.")
    else:
        from wsgiref.simple_server import make_server
        if args.bind not in ("127.0.0.1", "::1", "localhost"):
            parser.error("Local development server only binds to loopback; use the production deployment for HTTPS")
        with make_server(args.bind, args.port, Service(args.data)) as server:
            print(f"Local admin: http://{args.bind}:{args.port} (development only)", flush=True)
            server.serve_forever()


if __name__ == "__main__":
    main()
