"""Offline activation issuer. Only public.json is an APK build input; keep the issuer key private."""
from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path
import re
import uuid

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

PACKAGE = "com.shihab.diplay.preface"
SIGNER = "db0dc2a34dc06f22db7a3d10103fa011167712ebe61985ca2103084ff542cb82"
CONTACT = "starts181004"


def initialize(directory: Path):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    if any(directory.iterdir()):
        raise ValueError("私钥目录必须为空；不要覆盖已有签发私钥。")
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    private = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    for name, value in {"issuer-private.pem": private, "issuer-public.der": public}.items():
        with (directory / name).open("xb") as output:
            output.write(value)
        (directory / name).chmod(0o600)
    directory.chmod(0o700)


def load_key(directory: Path):
    key = serialization.load_pem_private_key((Path(directory) / "issuer-private.pem").read_bytes(), password=None)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size not in (3072, 4096):
        raise ValueError("签发私钥类型不支持")
    public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    if public != (Path(directory) / "issuer-public.der").read_bytes():
        raise ValueError("签发私钥与公钥不匹配")
    return key


def export_client(directory: Path, output: Path):
    key = load_key(directory)
    public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    target = Path(output) / "offline-license/public.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({"version": 1, "package": PACKAGE, "signer": SIGNER, "contact": CONTACT,
        "publicKey": base64.b64encode(public).decode("ascii")}, indent=2), encoding="utf8")


def normalize_device(device: str):
    if len(device) > 256:
        raise ValueError("设备码过长")
    device = "".join(device.split())
    if not re.fullmatch(r"DP-DEVICE1-[0-9a-fA-F]{64}", device):
        raise ValueError("请粘贴 APK 显示的完整设备码（DP-DEVICE1- 开头）")
    return device[len("DP-DEVICE1-"):].lower()


def issue(directory: Path, device: str):
    device = normalize_device(device)
    payload = "\n".join(("DP-OFFLINE1", device, PACKAGE, SIGNER, "permanent", str(uuid.uuid4()))).encode("ascii")
    signature = load_key(directory).sign(payload, padding.PKCS1v15(), hashes.SHA256())
    encode = lambda value: base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")
    return "DP-ACT1." + encode(payload) + "." + encode(signature)


def gui(directory: Path):
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk
    load_key(directory)  # Fail before showing the issuer UI if its private inputs are missing.
    window = tk.Tk()
    window.title("DiPlay 离线激活码工具 · 仅所有者使用")
    window.geometry("760x520"); window.minsize(580, 450)
    frame = ttk.Frame(window, padding=20); frame.pack(fill="both", expand=True)
    ttk.Label(frame, text="离线激活码工具", font=("Microsoft YaHei UI", 20)).pack(anchor="w")
    ttk.Label(frame, text="客户把设备码发给你 → 粘贴设备码 → 生成 → 将激活码或 TXT 文件发给客户。", wraplength=520).pack(anchor="w", pady=(8, 16))
    ttk.Label(frame, text="客户设备码（DP-DEVICE1- 开头）").pack(anchor="w")
    device = tk.Text(frame, height=3, wrap="char"); device.pack(fill="x", pady=6)
    status = tk.StringVar(value="单设备永久授权，无服务器，无到期时间；发出后无法远程停用。")
    result = tk.Text(frame, height=8, wrap="char", state="disabled")
    def generate():
        result.configure(state="normal"); result.delete("1.0", "end"); result.configure(state="disabled")
        try:
            token = issue(directory, device.get("1.0", "end"))
        except Exception as error:
            messagebox.showerror("无法生成", str(error)); return
        result.configure(state="normal"); result.delete("1.0", "end"); result.insert("1.0", token); result.configure(state="disabled")
        status.set("已生成。请复制完整激活码，或保存 TXT 文件发给客户。")
    ttk.Button(frame, text="生成永久激活码", command=generate).pack(anchor="w", pady=6)
    result.pack(fill="both", expand=True, pady=8)
    row = ttk.Frame(frame); row.pack(fill="x")
    def value():
        token = result.get("1.0", "end").strip()
        if not token:
            messagebox.showinfo("尚未生成", "请先填写设备码并生成激活码。")
        return token
    def copy():
        token = value()
        if token:
            window.clipboard_clear(); window.clipboard_append(token); status.set("已复制完整激活码。")
    def save():
        token = value()
        if not token: return
        path = filedialog.asksaveasfilename(defaultextension=".txt", initialfile="DiPlay-activation.txt", filetypes=[("激活码文件", "*.txt")])
        if path:
            Path(path).write_text(token + "\n", encoding="utf8"); status.set("激活码文件已保存。只需将此 TXT 文件发给客户。")
    ttk.Button(row, text="复制激活码", command=copy).pack(side="left", padx=(0, 10))
    ttk.Button(row, text="保存激活码 TXT", command=save).pack(side="left")
    ttk.Label(frame, textvariable=status, wraplength=520).pack(anchor="w", pady=(14, 4))
    ttk.Label(frame, text="请保管签发私钥及本工具；客户只需要 APK 和给他生成的激活码。联系微信：" + CONTACT, wraplength=520).pack(anchor="w")
    window.mainloop()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["init", "export-client", "issue", "gui"])
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--device")
    args = parser.parse_args()
    if args.action == "init":
        initialize(args.data); print("离线签发私钥已创建，请私下备份。")
    elif args.action == "export-client":
        if args.out is None: parser.error("--out is required")
        export_client(args.data, args.out); print("仅含公钥的 APK 配置已导出。")
    elif args.action == "issue":
        if args.device is None or args.out is None: parser.error("--device and --out are required")
        token = issue(args.data, args.device)
        with args.out.open("x", encoding="utf8") as output: output.write(token + "\n")
        print("激活码文件已保存。")
    else:
        gui(args.data)


if __name__ == "__main__":
    main()
