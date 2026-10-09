# 历史 Python 激活码后台

当前 `e01Licensed` 已改用 Workers 申请/批准流程，请使用 [Workers 部署说明](../license-workers/README.md)。此目录保留旧代码供历史追溯，不支持新版无需激活码的客户端。

后台已实现激活码生成、设备绑定、授权停用和恢复。联系微信固定为 **starts181004**。当前没有公网域名或服务器，本地运行不代表已经上线。

## 授权规则

- 每个激活码绑定一个安装实例的 RSA 公钥。私钥由 AndroidKeyStore 生成和保存，正常流程不导出；这不是硬件远程认证，不能据此保证设备不可伪装。
- 车机通过一次性随机挑战证明持有设备私钥，后台核验激活码/设备、有效期和停用状态后，用服务器私钥签发最长 5 分钟的连接许可。
- APK 只携带签发公钥和 HTTPS 地址。客户端按本次随机挑战验证签名，用单调计时计算许可剩余时间，不依赖车机日期正确。
- 许可只控制新建 CarPlay 会话。停用后停止签发许可；已经取得的许可最长仍可使用 5 分钟，运行中的会话不会被主动终止。重新启动应用或许可过期后，需要联网重新校验。没有离线宽限期。
- 用户先让车机联网完成授权，再连接 CarPlay。后台停用不影响原厂蓝牙恢复工具。
- 后台仅保存设备公钥的 SHA-256、授权状态、客户备注和时间。激活码存哈希，生成时仅显示一次。设备公钥用于校验请求，原始公钥不持久保存。不要在备注中填写不必要的个人信息。

## 本机试用（Windows）

在本目录打开 PowerShell。Python 3.11 以上：

```powershell
python -m venv .venv
& ./.venv/Scripts/python.exe -m pip install -r requirements.txt
& ./.venv/Scripts/python.exe app.py init --data ../license-private
& ./.venv/Scripts/python.exe app.py serve --data ../license-private
```

打开 `http://127.0.0.1:8787`。管理员密钥在 `../license-private/admin-token.txt`，复制到登录框。界面可生成激活码、刷新列表、停用/恢复授权。默认开发服务只监听本机；不要把它直接开放到公网。

私有目录含签发私钥、管理员密钥和数据库。不要发给客户、上传代码仓库或放进 APK。初始化只允许空目录，避免误换签发密钥使已发布 APK 无法校验。当前任务已创建的私有目录应继续保留，部署时使用同一签发密钥；如果选择重新初始化，则必须重新导出公钥并重新构建 APK。

## 正式部署（Linux + Docker Compose）

1. 准备服务器和域名，将域名 DNS 指向服务器，允许 TCP 80/443。
2. 上传本目录的源码，复制 `environment.example` 为 `.env`，把 `LICENSE_DOMAIN` 改成实际域名。示例域名不能直接使用。
3. 首次初始化新服务：

```sh
docker compose build
docker compose run --rm api python app.py init --data /data
docker compose up -d
```

如要沿用本地已生成的私钥和数据库，先创建命名卷、将整个私有目录安全迁移到该卷 `/data`，保持所有者 UID/GID 10001，并跳过 `init`。不要重新生成私钥后继续使用旧公钥构建的 APK。

4. Caddy 为实际域名申请 HTTPS 证书。打开 `https://你的域名` 登录后台。API 端口只在容器网络内开放。查看管理员密钥：

```sh
docker compose exec api cat /data/admin-token.txt
```

5. 从运行中的后台导出**仅含公钥**的 APK 配置：

```sh
docker compose exec api python app.py export-client --data /data --url https://你的域名 --out /data/client-public
docker compose cp api:/data/client-public ./client-public
```

`client-public/license/server.json` 可以用于构建 APK；不要把 `/data` 整体当作客户端配置目录。APK 构建会拒绝额外文件。

6. 在 Android 项目的原构建环境设置 `DIPLAY_LICENSE_ASSETS_DIR` 指向 `client-public`，保留已有认证、GOC 资产目录及安装包签名，然后执行：

```powershell
$env:DIPLAY_LICENSE_ASSETS_DIR = '客户端公钥配置目录的绝对路径'
$env:DIPLAY_LICENSE_PREVIEW = 'false'
./gradlew.bat :mobile:assembleStandaloneE01Licensed
```

必须继续执行 `docs/BUILD.md` 中完整 APK 的认证启动、签名、API 22 与实际资产检查。正式包还应在本车验证 AndroidKeyStore、HTTPS、联网激活及 CarPlay。Android 5.1 的系统 CA/TLS 状态可能影响连接，不能通过关闭证书检查来解决。

## 管理操作

登录后填写客户备注和有效天数（从生成时开始计时），生成激活码并保存。客户在 APK 授权页输入激活码。列表会显示设备码、激活状态和最近校验时间。

“停用”立即阻止后续签发；“恢复授权”重新允许校验，不改变原到期日或绑定设备。更换车机或清除应用数据后，先停用旧授权，再发新码。当前界面显示最近 1000 条授权，适合小规模部署。

管理员密钥只在页面内存中使用，不保存在浏览器存储。API 无跨域授权，页面文本通过 textContent 渲染。反向代理没有启用请求体日志；不要另加会记录管理员令牌、激活码或请求体的日志配置。

## 备份与运行

定期停下 API 容器后备份完整 `license-data` 卷，再启动服务，以保存一致的 SQLite 数据、签发私钥和管理员密钥。备份需要保存在受控的私有位置；不要使用会删除卷的 `docker compose down --volumes`。先验证可恢复再依赖备份。

服务器异常时，新会话无法取得授权，已经运行的 CarPlay 保持运行。没有自动扣费、客户通知、在线远程操作车机或修改固件的功能。

## 保护边界与许可

R8 提供代码压缩、优化和名称混淆；可反编译性不会消失。联网授权能控制这份官方客户端的正常使用，不能保证别人无法修改客户端、移除检查或重打包。安装签名自检也不是可信的硬件远程证明。

APK 为兼容既有 CarPlay 流程仍携带本地运行认证材料、原生 GOC 组件；R8 不会加密这些资产，接收者仍可能提取。这里的“服务器私钥不进 APK”仅指新增的授权签发私钥，不能混同于既有 CarPlay 运行认证。

项目继承 GPLv3。混淆不改变分发时的许可证义务，应保留署名、许可证并按 GPLv3 提供对应源码；不得把本方案宣传成闭源独占或不可破解。第三方二进制另有分发限制，现有本地个人包不能据此直接公开销售或上传。

当前提供的是本地个人验证工作流。Docker/HTTPS 配置需在实际服务器验证后才能视为部署完成。
