# DiPlay Preface 0.2.12 · E01 适配预览 2

新增 nFore／亿咖通原车连接识别，参考用户提供的 EasyPlay preview45 APK 字节码独立实现。手动选择手机优先；自动识别仅接受已配对设备，多个连接存在歧义时要求选择。厂商查询最多等待 250ms，接口不可用时回退原有路径。

适用目标：2020 款吉利星瑞 / GKUI / E01 / Android 5.1。保留 H.264、30 fps 和低负载画布设置。固件是否提供对应服务尚需实车验证，不能保证修复标准蓝牙或 RFCOMM 故障。

**这是不含配件认证材料的源码验证预览包，不能独立完成 iPhone 连接。**

## 下载与安装

下载主程序 APK，或下载 installers.zip 解压。合集只含主程序与本说明。应用名称 DiPlay E01 Legacy，包名 com.shihab.diplay.e01legacy，支持 ARMv7 / ARM64，最低 API 22。停车后用车机文件管理器安装。

与上一版包名和签名一致，可覆盖安装。本次保持 APK 内版本号 0.2.12-e01-android51-test / versionCode 31；请通过 E01.2 文件名及 SHA-256 区分此修订版。GitHub 自动生成的 Source code 压缩包不能安装。

## 验证与来源

APK 构建源码：`7ae9e60e76f97baf9fb0d35a0ae27d6b924c8c92`。
本地 1,270 项单元测试全部通过，Android 5.1 API 检查、APK 构建和签名验证通过。未完成实车验收。Release 标签指向 APK 构建源码。
