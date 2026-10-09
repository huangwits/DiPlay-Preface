# 原作者 0.2.14 适配记录

本次是星瑞功能适配：包名 `com.shihab.diplay.preface`、内部版本 `0.2.13.2 / 36`、最低 Android 5.1 保持不变。Geely 基准为 `cfcb6a96`，原作者基准为 `7887bb7b`。提交历史保留作者和来源。

| 范围 | 审查与适配 |
| --- | --- |
| Geely | 合并 cfcb6a96，修复新增报告导出和存储权限的旧 API 差异。 |
| 设置分类与搜索 | 2f5ec11e、6e6f7594；保留星瑞入口，搜索不启动 ADB 探测，待重连提示不打断会话。 |
| 默认连接与桌面入口 | 4137d3bb、4999f8cc、fb276ba0、471d0a5f、eeac74e0、10f19a6b、303d2fbe、5318a094。 |
| 定时日夜 | 34c971f6、18709ff6、f50c1fde、5c8ead43、2087dd4a、fb147c23；统一连接页也读取时间设置。 |
| 界面缩放 | 82d7a161、a1338216、603a1d67、e308b422；保留窗口回调、系统栏、简体中文和 API 22/23 locale 路径，不改 CarPlay 流密度。 |
| 图片选择与菜单 | d4543b34、25021d1c、1c1f1e21、9d1176b2、af70790a、ce45c07b、bc3f8f7f、a73d68fc；文档/内容选择器回退，封面等比方形填充。 |
| USB 与 RFCOMM | 5fc7a7af；有界尾部解析、较小请求回退和停滞诊断，保留 Geely 同步 USB 与权限恢复。 |
| 解密 | 5b2fb6e7、9506fdd4、54113a78；平台 ChaCha 回退和诊断，ThreadLocal 初始化兼容 API 22。 |
| 视频 | c62a8fcd、d43297bd、2502a57e、8ba6ec56、824f818d、df3e614f、13c137db、8ce1f9f9、f1e8a653、a4c9a98c；平滑视频默认关闭，保留 Geely 过期帧/关键帧恢复。 |
| Siri | 04c00165、6be6379f、b5cb86b0；保留工厂音源优先、旧 AudioRecord 构造器和 Geely 重试间隔。 |
| 可选通话处理 | f19a101a、806bbf6f 的通用媒体/SpeexDSP 部分；Saeed Safrini、shihabal3amri 及上游协作者署名保留。默认关闭，按流隔离参考，原生和系统 AEC 互斥，失败恢复系统 AEC。 |

## 继续使用 Geely 实现

- f2dab427、d3023656：Geely 已通过 `confirmedAddresses`、`manualHotspotAddresses` 和发布前核验提供双协议地址，保留车辆接口与凭据选择。
- faf4d963：Geely 已在首帧显示而隧道缺失时保留蓝牙，后到达的隧道仍由 `maybeCompleteWirelessHandoff` 接管。补齐无线控制循环无期限等待。未采用另一套回退状态机或 2174f1b1 基于版本猜测的快速分支。
- 5a05b2e1、01298fbc、387b7c6b：Geely 独立 `AudioFocusCoordinator` 已处理失焦静音、获焦恢复、请求代次隔离、工厂通话优先和麦克风所有权。
- bca77927 至 b63c23f1：保留 Geely 方向盘学习、车辆桥接与 Siri 操作，避免新增并列按键拥有者。
- BYD/DiLink 仪表、厂商通话状态写入、固定按键码、旋转屏几何、多语言及原作者包名/版本不在本分支范围。

## 本地验证（2026-10-07）

- common：642 项，640 项通过；未提供实际 APK 时按设计跳过 2 项认证启动测试。shared：739 项全部通过。
- 完整 APK 构建后单独运行上述 2 项认证启动测试，全部通过、无跳过。常规回归报告已在运行这两项测试前归档。
- API 22 NewApi lint、源码 APK 认证资源排除检查及 5 项维护脚本测试通过。
- 完整 APK 保留原签名、Preface 包名和 0.2.13.2 / 36 元数据；ARM32/ARM64、简体中文资源、认证输入和本地辅助工具逐项核验通过。
- 设置测试从实际“车辆”入口检查控件；热点搜索和权限测试共用带状态重置的模拟对象，修复整套回归中 Kotlin 单例复用旧模拟类型造成的异常。授权异常同时记录诊断日志。

本地辅助工具及认证资料在源码树外提供，不随源码发布。桌面测试不能代替 E01 实车和 iPhone 验证。

Source attribution and Geely ownership of vehicle-specific behavior are preserved. Optional call processing includes the SpeexDSP subset and its original BSD-style license. Local helper and authentication inputs remain outside this source tree.
