# DiPlay Preface 0.2.12 · E01 Android 5.1 预览版

同步 carlito12345/DiPlay 的 `84050d6`，包含原作者 shihabal3amri/DiPlay 的 `2fc876e`。目标为 2020 款星瑞 / GKUI / 亿咖通 E01 / MT6735 / Android 5.1（API 22）。

## 下载

在下方 **Assets** 下载 APK；也可下载安装包合集 ZIP 后解压。GitHub 自动生成的 Source code 压缩包是源码，不能安装。

| 附件 | 用途 |
| --- | --- |
| `DiPlay-Preface-0.2.12-E01-Android51-source-only.apk` | 0.2.12 E01 主程序源码验证包，ARMv7/ARM64。**不含配件认证材料，不能作为独立连接 iPhone 的完整车测包。** |
| `DiPlay-E01-Bluetooth-Diagnostics-v03.apk` | 独立诊断工具；导出本车蓝牙接口资料 ZIP，无需 root、电脑 ADB 或先开启蓝牙。与此前 v03 发布附件相同。 |
| `DiPlay-Preface-v0.2.12-preface-e01.1-installers.zip` | 上述两个 APK 和中文安装说明。 |
| `SHA256SUMS.txt` | APK、说明和合集的 SHA-256 校验值。 |

停车后通过已可用的 U 盘或文件传输方式安装。主程序仍为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`；签名与本项目此前 E01 测试版一致，可覆盖升级保留设置。

## 更新内容

- 自动无线连接、热点就绪检测与有上限的首次 TCP 超时重试；保留同一 Wi-Fi 模式。
- 30%–160% 分辨率与解码能力回退；E01 仍限制 H.264、30 fps、画布长边 960／短边 540。
- 方向盘焦点操作、仪表内容实时切换、封面与导航卡片恢复。
- 恢复吉利设置、HUD 导航、版本入口和主动诊断上传；保留原厂蓝牙音频交接。
- 补回 Android 5.1 系统服务调用和默认网络识别；热点归属不明确或网络在采样时变化，会保持未就绪，不把普通上行网卡误认为热点。

**E01 厂商蓝牙数据通道仍未实现。** 自动无线模式和原厂蓝牙音频交接都不能替代 RFCOMM/iAP2 通道。当前仍需用诊断 v03 导出并提供本车资料；尚未完成 E01 实车连接、音视频或重连验收。

## 验证

本地 1,276 项测试通过（common 552、shared 712、diagnostics 12），0 失败、0 错误、0 跳过。common/shared/mobile 的 NewApi 专项扫描、诊断模块完整 lint 和主程序源码验证包构建通过。新增 7 项热点采样检查与 1 项 E01 分辨率回归检查通过。

API 22 分支在 Robolectric API 23 沙箱内模拟，不能替代 Android 5.1 实机验证。公开源码和主程序 APK 已检查不含配件认证材料。蓝牙诊断 v03 的签名、校验值和下载验证沿用其独立发布记录。
