# 授权服务部署与维护

此文档面向自行部署或维护授权服务的开发者。使用项目正式 APK 的普通用户无需执行这些步骤。当前方案由一个 Cloudflare Pages 项目处理申请、管理和清理，不需要第二个 Worker 或 Cron。

## 本地验证

使用 Node.js 22.12 或更新版本，在 `license-workers` 目录执行：

```sh
npm ci
npm test
npm run check
```

测试使用临时密钥、workerd 和本地 D1。`npm run check` 是构建检查，不会部署服务。可将 `DIPLAY_WORKERS_VECTOR` 设置为源码外的 JSON 路径，生成签名样例并用于 Android `WorkersInteropTest` 验证。

## Pages 部署

新部署需准备 Cloudflare 账户和 D1 数据库；维护已有服务时沿用原数据库、签发密钥和管理员令牌，不重新初始化或清空数据。

1. 使用 Wrangler 登录并取得 Pages/D1 所需权限。新服务创建 D1，依次应用 `migrations/0001.sql` 和 `migrations/0002_diagnostic_reports.sql`。已有数据库先备份，再仅应用尚未执行的增量迁移；不重复初始化。启用诊断报告版本前必须先创建报告表，旧版服务可继续使用原有表。
2. 在源码外建立部署目录，把 `wrangler.pages.example.json` 复制为其中的 `wrangler.json`，填写项目名及数据库 ID，保留 `DB` 绑定、包名和签名摘要。需要选择账户时使用 `CLOUDFLARE_ACCOUNT_ID`，不要把 `account_id` 写入 Pages 配置。
3. 使用已安装的 esbuild 将 `src/worker.js` 打包为部署目录的 `public/_worker.js`：

```sh
esbuild src/worker.js --bundle --format=esm --platform=browser --outfile=/your/deploy/public/_worker.js
```

4. 新项目在部署目录执行 `wrangler pages project create <项目名> --production-branch main`。已有项目跳过创建。
5. 新服务使用 `setup.mjs` 在源码外的全新私有目录生成配置，URL 指向最终 Pages origin。维护已有服务时沿用匹配的配置，不重新签发密钥：

```sh
node setup.mjs --out /private/diplay-online --url https://your-project.pages.dev
```

私有目录包含 `server-secrets.json`、`admin-token.txt` 及 `client/license/server.json`。前两者只供部署和管理使用；客户端文件只含服务地址、验签公钥和软件标识。请私下备份，不要将整个目录作为 APK 资源。

6. 新服务从部署目录写入 Secrets 并部署 `public` 目录：

```sh
wrangler pages secret bulk /private/diplay-online/server-secrets.json --project-name <项目名>
wrangler pages deploy public --project-name <项目名> --branch main
```

只更新已有服务代码时，不需要重复上传或更换 Secrets。上传目录只能是已打包的 public 目录。不要用未指定 Pages 的 `wrangler deploy` 另建一个 Worker。

## 更新后核对

GitHub 的 `.github/workflows/pages.yml` 发布的是 GitHub 项目介绍站点，不会部署上述 Cloudflare 授权服务。修改 README、提交到 main 或看到 GitHub Actions 成功，均不能单独证明授权后台更新成功。

部署后台后，使用普通网络访问 `/admin`、`/admin.css`、`/admin.js`，将实际响应与本次源代码生成的内容比较。验证原管理员令牌可登录、列表/搜索/筛选正常；涉及协议修改时，用专门的测试安装验证申请、批准、重新进入及停用后的新连接检查。不要为了测试修改真实用户的授权。

保持正常 HTTPS 验证；不要通过关闭证书校验绕过问题。需要清理历史部署时，先备份并验证当前生产版本，再按已明确的维护范围处理。

## Android 客户端

正式线上授权变体为 `e01Licensed`。自行构建时设置 `DIPLAY_LICENSE_ASSETS_DIR` 指向上述私有目录中的 `client`，并按 [完整构建说明](../docs/BUILD.md) 提供所需运行输入与安装签名，运行 `:mobile:assembleStandaloneE01Licensed`。

客户端、后台的包名、安装签名及验签配置必须一致。更换签名无法直接覆盖原安装；更换服务地址或签发密钥也需要同步客户端。空服务地址仅用于明确标识的不可激活预览。历史离线变体不会因部署网页自动变成线上版。

## 数据与密钥

连接许可只缓存在当前进程中，最长 5 分钟；按安装公钥、包名、签名摘要及一次性挑战绑定。过期或进程重启后的新无线连接需要联网复核；活动 CarPlay 会话与恢复操作不受影响。

D1 保存设备公钥摘要、申请号、授权状态、有效期和查询时间。诊断表额外保存用户主动提交的描述、版本、设备摘要与已过滤的应用日志，不主动采集通讯录、聊天或配件身份。自动过滤不能识别任意自由文本中的全部个人信息，用户不应在描述中填写个人信息。边缘服务会处理请求 IP；应用限流使用短期摘要。不要记录请求体、管理员令牌或 Authorization 请求头。

正常挑战请求和已登录的管理员列表查询会分批清理过期挑战/限流、30 天未查询的待批准申请及超过 180 天的审批事件，每类每次最多 100 条。无请求时不会运行。已批准和已停用设备保留，避免重新申请绕过停用。

诊断报告从提交起 7 天后不可读取，并在正常 API 流量中清理。每台安装每天最多 5 份，最多保存 100 份未过期报告；容量满时明确拒绝新上传。客户端保留摘要和最近日志，最多 512 KiB，服务器上限 1 MiB。相同安装提交相同文档返回原编号；报告内容参与一次性安装密钥签名，不能复用授权请求签名。上传不申请激活，也不改变授权决定或连接租约，未批准设备同样可以反馈故障。既有 TLS 兼容根库也用于此同源 HTTPS 上传，仍验证证书和域名。

后台登录后可按报告编号或授权申请号检索、阅读、下载和删除报告。列表不返回报告正文；正文与描述均按纯文本呈现，所有报告接口要求现有管理员令牌。报告编号本身不是读取凭据。备份可能包含诊断数据，应私下保管并按实际保留策略清理备份。

此入口替换的是设置中的诊断报告上传。独立车辆属性扫描模块的历史上游上传路径不在本次迁移范围内。

定期备份 D1 和签发密钥。签名私钥、签发私钥、管理员令牌和数据库不得进入源码或 APK；运行身份和授权签发密钥是不同用途的材料。保留项目许可证和第三方声明。


## 旧安卓授权 TLS 兼容（0.2.16.2）

部分 E01 的系统 CA 库无法验证现有 Pages HTTPS 链。2026-10-10 实测服务链为 Let’s Encrypt YE1 → Root YE → ISRG Root X2，TLS 1.2 握手正常。Android 5.1 的固件根证书更新情况不同，不能根据车型名称推定证书可用。

客户端在 API 22–25 的授权连接上保留系统信任，并以应用内公开 ISRG Root X1/X2 作为补充。只修改该 HttpsURLConnection 的 SSLSocketFactory；不修改全局工厂、主机名校验、证书有效期校验或授权签名/租约规则。证书错误会提示检查日期、时间、网络和应用版本；不提供跳过验证选项。现代安卓仍使用系统工厂。此修复不等于完成领克 06 的原厂蓝牙实车适配。

公开 DER 证书位于 `common/src/main/res/raw/license_isrg_root_x1.der` 和 `license_isrg_root_x2.der`，来自 [ISRG 官方证书库](https://letsencrypt.org/certificates/)。SHA-256 分别为 `96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6` 和 `69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470`。它们仅包含公开 CA 证书，没有私钥或设备身份。证书轮换后检查新链能否锚定到这些根；若 CA 变化，应核实官方证书并重新验证客户端。

验证包括旧根库缺失、未知颁发者、篡改签名、过期/未来证书、系统信任保留、全局 TLS/域名校验不变及官方根指纹。可设置 `DIPLAY_TLS_CHAIN` 为新抓取的公开 PEM 证书链，运行 `LicenseTlsTest` 检查实际链；未提供时该项为条件测试。来源：[Android TLS 指引](https://developer.android.com/privacy-and-security/security-ssl)、[ISRG 客户端兼容性](https://letsencrypt.org/docs/certificate-compatibility/)。
