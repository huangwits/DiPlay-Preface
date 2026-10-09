# 星瑞维护适配（2026-10-08）

从 carlito12345/DiPlay 的 `79194d65`、`08aa5fd3` 选择性适配以下改动，保留原作者及 carlito 署名。连接基线和 Android 5.1 专用修复继续来自当前星瑞分支；这不是整体升级到上游 0.2.14。

- 音频启动、RTP 和停止回调检查关闭状态，并与播放器创建、会话关闭共用监视器。关闭后到达或等待锁的回调不会重建播放器、通话状态和回声参考。保留现有独立音频路由、原厂通话/Siri 麦克风及可选回声处理。
- 本地扫描报告使用 `DiPlay-Vehicle-...` 名称与独立备用目录，扫描和连接诊断分别保留最近八份；公共下载目录仍不自动轮换删除。保留 API22 的 ContextCompat 权限检查、文件选择器缺失和存储不可用回退。新增路径仅授权相应报告目录。
- 本地扫描报告补充车机、系统、应用、车桥版本和车型来源，原始属性行保持不变；无法识别车型时明确标记。扫描仍依赖兼容车桥和车辆接口。既有上传仍提交原始扫描数据，上传文件名及服务端契约未变化，也未部署服务器。
- 默认车辆按钮、设置预览和恢复默认使用同一内置吉利图标。默认名称为“吉利”；历史 BYD/空默认值更新，已有 Geely 或其他非空自定义名称、图片保留。图标出处见 [GEELY-OEM-ICON.md](GEELY-OEM-ICON.md)。

API22 同步 USB、低版本分包、拔出后旧节点失效检测、Android 8 的 16 KiB 请求上限、车机热点识别和独立音频配置保留。GD 0.11.24 原包要求 API28，未内置；新的蓝牙输出及强制两组声音模式未纳入本次适配。

修复前新增的三项音频回归和两项报告保留回归均失败；修复后定向检查通过。音频竞争测试让回调阻塞在资源锁上，再先关闭会话，以覆盖只在锁外检查 closed 的遗漏。

完整回归 common 共 649 项：647 项通过，未传入实际 APK 时按设计跳过两项认证启动测试；shared 744 项全部通过，合计 1391 项常规回归通过。新增的两项扫描元信息测试验证 API22 分支、原始行保留、未知车型和重复导出。API22 NewApi lint 无问题，源码 APK 检查、品牌范围检查和五项维护脚本测试通过。车标范围检查仅放行已核验的吉利图像 SHA-256，继续拒绝其他图像占用历史资源名。

回归 XML 已归档，防止后续独立安装包启动测试覆盖整套测试记录。完整 APK 交付仍要求两项认证启动测试通过且无跳过，并核验原签名、外部认证输入及本地工具。校验记录保存在源码树外 `maintenance-20261008`，最终结果随安装包说明交付。

本地交付沿用 `com.shihab.diplay.preface`、`0.2.13.2 / 36`、原签名和 Android5.1 支持。凭据、签名密钥及本地 GOC 兼容组件位于源码树外；完整个人测试包不发布到 GitHub。按所有者随后确认的实测结果，已移除 mtk-su 备用方案和蓝牙工具日志，仅保留原厂 GOC 流程；USB 授权流程未改动。桌面测试不能证明我们的 E01/iPhone 适配包已在实车连接成功。

恢复构建时发现并修正四处 API22 兼容检查错误：三个并发集合改用低版本可用的 `Collections.newSetFromMap`，视频 Surface 分离在 API23 以下释放解码器，不调用 `setOutputSurface`。新增回归覆盖 API22 解码器释放；本次最终验证和产物哈希记录在源码树外 `work/validation`。前述回归数量属于较早的维护检查，不能代替当前产物的验证记录。

Selective maintenance from carlito12345/DiPlay, retaining author attribution and the Preface/API22 baseline: serialize audio callbacks with closure, isolate local report retention, add scan metadata without changing upload payloads, and share the bundled Geely badge across settings and CarPlay. Physical vehicle validation remains pending.
