# E01 蓝牙诊断 v03（预览版）

用于 2020 款星瑞 / GKUI / 亿咖通 E01 / Android 5.1（最低 API 22）。新增 **“导出 E01 蓝牙适配资料 ZIP”**，收集固件标识、系统蓝牙应用和服务声明、可读取的候选 APK/JAR/代码库及文件校验清单，帮助定位原厂蓝牙接口。

**本版是诊断工具，不是蓝牙连接修复版。E01 厂商 RFCOMM/iAP2 数据通道尚未实现，未完成 E01 实车验收。**

## 下载和使用

1. 在下方 **Assets** 下载 `DiPlay-E01-Bluetooth-Diagnostics-v03.apk`。GitHub 自动生成的 Source code 压缩包不能安装。
2. 停车后安装，打开 **DiPlay 蓝牙诊断**。与 v02 包名和签名一致，可覆盖升级。
3. 点击 **导出 E01 蓝牙适配资料 ZIP**；不要求先开启蓝牙、配对 iPhone、root 或连接电脑 ADB。
4. 选择 **保存到 U 盘／文件** 或 **分享 ZIP**。没有系统文件选择器时，按弹窗显示的路径用文件管理器复制。
5. 将导出的 ZIP 返回用于继续分析本车接口。

常见导出目录为 `Android/data/com.shihab.diplay.diagnostics/files/Download/interface-bundles/`，以弹窗实际路径为准。仅有报告、缺少可读固件的 ZIP 仍可能不足以实现适配。

附件另提供中文使用说明和 `SHA256SUMS.txt`。此独立诊断版无需配件认证材料，不含主程序 APK。

## 公开案例调查

查到同型号 E01/MT6735/Android 5.1 的工具故障记录、E02 厂商栈与 MTK 栈切换实验、ECARX 蓝牙/iAP2 接口源码及外置盒子案例。**未找到已验证可直接应用到本车的无线蓝牙修复。** E02 实验针对 Android 9，不能直接移植到 E01 Android 5.1。

[查看案例、原始链接与适用范围](../E01-BLUETOOTH-PUBLIC-RESEARCH.md)。

## 验证与范围

- 12 项诊断模块单元测试通过，0 失败、0 错误；诊断 APK 构建与完整 `lintRelease` 通过（有告警）。
- APK 最低 API 22，版本 `0.3-e01-interface` / versionCode 3；v1/v2 签名验证通过，签名与 v02 一致。
- 发布 APK 与本地构建产物的 SHA-256 一致；未进行本车安装、资料导出或无线连接验收。
- 导出按钮不修改蓝牙开关、不调用未知厂商 Binder 事务、不启动厂商服务、不读取联系人或配对记录，也不自动上传资料。
- 固件代码单文件上限 96 MiB、总上限 192 MiB；缺失、不可读或超过上限的条目会记录到 `files.tsv`。
