"""Generate one self-contained local HTML page from the public APK configuration."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re


def build(configuration: Path, output: Path):
    config = json.loads(configuration.read_text(encoding="utf8"))
    if set(config) != {"version", "package", "signer", "contact", "publicKey"} or config["version"] != 1:
        raise ValueError("Only public APK configuration is accepted")
    if config["package"] != "com.shihab.diplay.preface" or not re.fullmatch(r"[0-9a-f]{64}", config["signer"]) or config["contact"] != "starts181004":
        raise ValueError("Unexpected package, signer or contact")
    public = base64.b64decode(config["publicKey"], validate=True)
    if not 256 <= len(public) <= 1024: raise ValueError("Unexpected public key size")
    html = Path(__file__).with_name("issuer.template.html").read_text(encoding="utf8")
    html = html.replace("__PUBLIC_CONFIG_JSON__", json.dumps(config, ensure_ascii=True).replace("<", "\\u003c"))
    script = re.search(r"<script>([\s\S]*?)</script>", html).group(1)
    csp_hash = base64.b64encode(hashlib.sha256(script.encode("utf8")).digest()).decode("ascii")
    html = html.replace("__SCRIPT_HASH__", csp_hash)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(html, encoding="utf8", newline="\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    build(args.config, args.out)
    print("Local HTML generated from public configuration; no issuer private key included.")
