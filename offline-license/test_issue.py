import base64
import json
from pathlib import Path
import tempfile
import unittest

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

import issue


def decode(value):
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


class IssuerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.private = self.root / "private"
        issue.initialize(self.private)

    def tearDown(self):
        self.temp.cleanup()

    def test_issue_roundtrip_binds_expected_device_package_and_signer(self):
        code = issue.issue(self.private, "DP-DEVICE1-" + "A" * 64)
        prefix, payload, signature = code.split(".")
        self.assertEqual("DP-ACT1", prefix)
        payload, signature = decode(payload), decode(signature)
        public = serialization.load_der_public_key((self.private / "issuer-public.der").read_bytes())
        public.verify(signature, payload, padding.PKCS1v15(), hashes.SHA256())
        self.assertEqual(["DP-OFFLINE1", "a" * 64, issue.PACKAGE, issue.SIGNER, "permanent"], payload.decode().splitlines()[:5])
        with self.assertRaises(InvalidSignature):
            public.verify(signature, payload.replace(b"permanent", b"unlimited"), padding.PKCS1v15(), hashes.SHA256())

    def test_invalid_device_and_extra_fields_are_rejected(self):
        for device in ("", "a" * 64, "DP-DEVICE1-" + "g" * 64, "DP-DEVICE1-" + "a" * 64 + "\nother", "x" * 257):
            with self.assertRaises(ValueError): issue.issue(self.private, device)

    def test_initialization_preserves_existing_key_and_mismatched_pair_cannot_issue(self):
        original = (self.private / "issuer-private.pem").read_bytes()
        with self.assertRaises(ValueError): issue.initialize(self.private)
        self.assertEqual(original, (self.private / "issuer-private.pem").read_bytes())
        other = self.root / "other"; issue.initialize(other)
        (self.private / "issuer-public.der").write_bytes((other / "issuer-public.der").read_bytes())
        with self.assertRaises(ValueError): issue.issue(self.private, "DP-DEVICE1-" + "a" * 64)

    def test_client_export_has_only_public_configuration_and_no_server(self):
        output = self.root / "client"
        issue.export_client(self.private, output)
        self.assertEqual(["offline-license/public.json"], [p.relative_to(output).as_posix() for p in output.rglob("*") if p.is_file()])
        config = json.loads((output / "offline-license/public.json").read_text())
        self.assertEqual({"version", "package", "signer", "contact", "publicKey"}, set(config))
        self.assertEqual((self.private / "issuer-public.der").read_bytes(), base64.b64decode(config["publicKey"]))


if __name__ == "__main__": unittest.main()
