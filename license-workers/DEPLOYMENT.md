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

1. 使用 Wrangler 登录并取得 Pages/D1 所需权限。新服务创建 D1 并应用 `migrations/0001.sql`；已有数据库不重复初始化。
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

D1 保存设备公钥摘要、申请号、授权状态、有效期和查询时间；不保存电话、微信内容、车架号或配件身份。边缘服务会处理请求 IP；应用限流使用短期摘要。不要记录请求体、管理员令牌或 Authorization 请求头。

正常挑战请求和已登录的管理员列表查询会分批清理过期挑战/限流、30 天未查询的待批准申请及超过 180 天的审批事件，每类每次最多 100 条。无请求时不会运行。已批准和已停用设备保留，避免重新申请绕过停用。

定期备份 D1 和签发密钥。签名私钥、签发私钥、管理员令牌和数据库不得进入源码或 APK；运行身份和授权签发密钥是不同用途的材料。保留项目许可证和第三方声明。
