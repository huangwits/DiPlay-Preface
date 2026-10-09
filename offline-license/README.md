# 离线激活工具

此工具仅用于已有离线授权版本。当前发布版使用线上授权，无需使用本工具。

## 签发激活码

1. 打开 `DiPlay-offline-activation.html`，或运行 `OPEN-ISSUER.cmd`。
2. 选择与目标版本匹配的 `issuer-private.pem`。
3. 粘贴设备码，生成激活码并交给对应使用者。
4. 在车机上粘贴或导入激活码；支持手机协助的版本也可按页面提示提交。

私钥只在当前页面内使用，刷新或关闭后需重新选择。不要将私钥发给使用者，也不要重新生成密钥来给旧版本签发。迁移电脑时应保留原有密钥及备份。

离线授权为永久授权，签发后无法远程撤销。卸载、清除数据或更换设备可能需要重新签发。

## 从源码生成网页

在仓库根目录执行：

```sh
python offline-license/build_web.py --config mobile/src/e01Public/assets/offline-license/public.json --out DiPlay-offline-activation.html
```

## 备用工具

在本目录使用 Python 3.11+（含 Tkinter）：

```powershell
python -m venv .venv
& ./.venv/Scripts/python.exe -m pip install -r requirements.txt
& ./.venv/Scripts/python.exe issue.py gui --data '私有签发目录'
```

## English

This tool supports existing offline-licensed builds. The current release uses online approval and does not need this tool. Open the local issuer page, select the matching existing private key, and generate a code for the installation's device code. Keep the key private and backed up. Permanent offline codes cannot be revoked remotely.
