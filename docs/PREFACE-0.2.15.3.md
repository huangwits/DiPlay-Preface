# Preface 0.2.15.3

基于已核实的 Carlito 0.2.15 版本编号，本地修订 3，versionCode 43。保持已选择的 Android 5.1 连接基线。

- 修复首页将闲置后台控制器当成已连接会话、直接进入投屏页的问题。未激活开始无线连接必须进入授权；桌面图标仅对已有活动 CarPlay 会话直接恢复。
- 投屏控制器按照本次实际无线/USB 模式做授权检查，避免读取旧 USB 设置而跳过无线授权。
- USB 继续免激活；已有活动 CarPlay 会话和蓝牙恢复操作继续独立于授权。
- 浏览器管理员后台改为状态概览、全库搜索、状态筛选和响应式列表；明确区分有效、到期、待批准、停用。审批确认框显示申请号与有效天数。
- 后台审批、密钥、管理员令牌、D1 数据与客户端协议保持兼容。车机保持每分钟查询及手动刷新。

新安装包沿用原签名、Pages 配置、认证/GOC 输入和 R8 混淆。仅作本地交付，公开分发只含源码。保留 0.2.15.2 历史包。桌面回归不能替代实车验证。

## English

Preface revision 0.2.15.3 / 43 corrects wireless admission at home/launcher entry and checks the actual transport at host startup. USB remains activation-free and active CarPlay sessions continue uninterrupted. The existing Pages admin gains responsive status summaries, database-wide search, expiry filters, pagination and explicit approval/revocation dialogs. Existing credentials, data and client protocol are preserved. Local installers only; public distribution remains source-only.
