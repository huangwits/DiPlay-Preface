import base64
import concurrent.futures
import io
import json
from pathlib import Path
import tempfile
import unittest

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from app import ApiError, PACKAGE, SIGNER, Service, b64, canonical, initialize, normalize_code, sha


class LicensingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        initialize(self.directory)
        self.now = 1791430000
        self.service = Service(self.directory, clock=lambda: self.now)
        self.key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        self.issued = self.service.issue({"label": "测试客户", "days": 1})

    def tearDown(self):
        self.temp.cleanup()

    def request(self, action="activate", key=None, code=None):
        key = key or self.key
        public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        challenge = self.service.challenge()
        code = normalize_code(code or self.issued["code"]) if action == "activate" else ""
        proof = canonical(action, challenge["id"], challenge["nonce"], sha(public), public, code, PACKAGE, SIGNER)
        return {"challengeId": challenge["id"], "deviceId": sha(public), "package": PACKAGE,
            "signer": SIGNER, "publicKey": b64(public), "code": code,
            "signature": b64(key.sign(proof, padding.PKCS1v15(), hashes.SHA256()))}

    def error(self, action, request, code):
        with self.assertRaises(ApiError) as caught:
            self.service.authorize(action, request)
        self.assertEqual(code, caught.exception.code)

    def http(self, path, request=None, authorization="", method="POST"):
        body = json.dumps(request or {}).encode()
        environment = {"PATH_INFO": path, "REQUEST_METHOD": method, "REMOTE_ADDR": "127.0.0.1",
            "CONTENT_TYPE": "application/json", "CONTENT_LENGTH": str(len(body)),
            "HTTP_AUTHORIZATION": authorization, "wsgi.input": io.BytesIO(body)}
        result = {}
        def start(status, headers):
            result.update(status=int(status.split()[0]), headers=dict(headers))
        result["body"] = b"".join(self.service(environment, start))
        return result

    def test_activation_refresh_and_signed_challenge_bound_lease(self):
        for action in ("activate", "refresh"):
            request = self.request(action)
            envelope = self.service.authorize(action, request)
            payload = base64.b64decode(envelope["payload"])
            self.service.key.public_key().verify(base64.b64decode(envelope["signature"]), payload,
                padding.PKCS1v15(), hashes.SHA256())
            values = payload.decode().split("\n")
            self.assertEqual(["DP1", self.issued["id"], request["deviceId"], str(self.now), str(self.now+300), PACKAGE, SIGNER, request["challengeId"]], values)
        row = self.service.listing()["licenses"][0]
        self.assertEqual(request["deviceId"], row["device"])
        self.assertNotIn("code_hash", row)
        self.assertNotIn(self.issued["code"], json.dumps(row))

    def test_device_cannot_reuse_another_devices_code(self):
        self.service.authorize("activate", self.request())
        other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        self.error("activate", self.request(key=other), "device_already_bound")
        self.error("refresh", self.request("refresh", key=other), "license_unavailable")

    def test_proof_tamper_replay_and_expired_nonce_are_rejected(self):
        request = self.request()
        self.service.authorize("activate", request)
        self.error("activate", request, "challenge_expired")
        request = self.request(); request["signature"] = b64(b"x"*256)
        self.error("activate", request, "invalid_device_proof")
        request = self.request(); self.now += 121
        self.error("activate", request, "challenge_expired")

    def test_unbound_device_wrong_signer_or_package_cannot_refresh(self):
        self.error("refresh", self.request("refresh"), "license_unavailable")
        for field, value in (("signer", "a"*64), ("package", "com.other.app"), ("deviceId", "b"*64)):
            request = self.request(); request[field] = value
            self.error("activate", request, "activation_rejected")

    def test_disabled_and_expired_licenses_stop_issuing_leases(self):
        self.service.authorize("activate", self.request())
        self.service.set_enabled({"id": self.issued["id"], "enabled": False})
        self.error("refresh", self.request("refresh"), "license_unavailable")
        self.service.set_enabled({"id": self.issued["id"], "enabled": True})
        self.service.authorize("refresh", self.request("refresh"))
        self.now += 86300
        envelope = self.service.authorize("refresh", self.request("refresh"))
        fields = base64.b64decode(envelope["payload"]).decode().split("\n")
        self.assertEqual(100, int(fields[4])-int(fields[3]))
        self.now += 101
        self.error("refresh", self.request("refresh"), "license_unavailable")

    def test_concurrent_activation_binds_exactly_one_device(self):
        other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        requests = [self.request(), self.request(key=other)]
        def activate(request):
            try:
                self.service.authorize("activate", request)
                return "ok"
            except ApiError as error:
                return error.code
        with concurrent.futures.ThreadPoolExecutor(2) as executor:
            results = list(executor.map(activate, requests))
        self.assertCountEqual(["ok", "device_already_bound"], results)

    def test_admin_requires_credential_and_does_not_leak_codes(self):
        self.assertEqual(401, self.http("/admin/list")["status"])
        admin = "Bearer " + (self.directory/"admin-token.txt").read_text()
        response = self.http("/admin/list", authorization=admin)
        self.assertEqual(200, response["status"])
        self.assertNotIn(b"code_hash", response["body"])
        issued = self.http("/admin/issue", {"label": "<script>alert(1)</script>", "days": 2}, admin)
        self.assertEqual(200, issued["status"])
        self.assertEqual(200, self.http("/admin/state", {"id": self.issued["id"], "enabled": False}, admin)["status"])
        self.assertEqual("no-store", response["headers"]["Cache-Control"])
        self.assertNotIn("Access-Control-Allow-Origin", response["headers"])

    def test_public_routes_reject_bad_shape_and_rate_limit(self):
        self.assertEqual(200, self.http("/", method="GET")["status"])
        self.assertEqual(405, self.http("/v1/challenge", method="GET")["status"])
        self.assertEqual(400, self.http("/v1/activate", {"bad": True})["status"])
        for _ in range(240):
            last = self.http("/v1/challenge")
        self.assertEqual(429, last["status"])

    def test_issuer_keys_are_not_overwritten_and_invalid_admin_inputs_fail(self):
        before = (self.directory/"signing-public.der").read_bytes()
        with self.assertRaises(ValueError): initialize(self.directory)
        self.assertEqual(before, (self.directory/"signing-public.der").read_bytes())
        for data in ({"days": 0}, {"days": True}, {"days": 3651}, {"label": "x"*101}):
            with self.assertRaises(ApiError): self.service.issue(data)
        with self.assertRaises(ApiError): self.service.set_enabled({"id": "missing", "enabled": True})


if __name__ == "__main__":
    unittest.main()
