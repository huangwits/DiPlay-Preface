# DiPlay 星瑞 E01 安卓 5.1 适配

目标车辆：2020 款吉利星瑞旗舰，GKUI，亿咖通 E01（MT6735），Android 5.1 / API 22。

以 [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) 的 `049e080`（0.2.11）为基准，移入旧安卓兼容层并保留吉利改动。原作者为 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)，旧安卓兼容代码来自 [DiPlay-Geely-Android43](https://github.com/xikai6282/DiPlay-Geely-Android43)。

- `android51-e01`：本项目维护的适配分支。
- `main`：保留 Fork 时的上游版本，不是安卓 5.1 适配版。
- Fork 的 `main` 已有更新到 `6b2b3b9`，这些较新改动尚未合入本次验证版本，不宣称已经同步最新上游。

## 当前状态

已完成 API 22 兼容修改、E01 低负载默认设置及 ARMv7/ARM64 打包。common/shared 共 833 项本地测试通过；三个模块 NewApi 检查通过。尚未通过 E01 实车连接验收。

**E01 厂商无线蓝牙尚未实现。** 原厂电话和音乐正常，但本车没有向 DiPlay 提供标准 Android 蓝牙适配器。原厂蓝牙可用不代表第三方应用能建立所需的 RFCOMM 数据通道。

仓库包含新版 `diagnostics` 工具：在车机点击“识别 E01 原厂蓝牙应用（无需电脑）”，可读取固件、相关系统应用包名和系统库线索，再拍照提供结果。该按钮不会启动原厂服务或修改蓝牙开关。

## 下载与构建

进入 **Actions → Android 5.1 E01 checks**，成功后下载 `source-only-e01-and-bluetooth-diagnostics`：

- E01 主程序是**无配件认证材料的源码验证包**，不能把它当成可独立连接 iPhone 的完整车测包。
- 蓝牙诊断 APK 可独立使用，不需要配件认证材料。

完整车测包采用明确提供的本地认证输入运行 `:mobile:assembleStandaloneE01`。仓库不上传认证密钥、证书或本地完整车测 APK。[构建说明](docs/BUILD.md)

后续从 carlito 同步更新时，使用 **Actions → Review carlito updates → Run workflow**。流程只生成草稿 PR 并运行检查，不自动合入适配分支。有冲突时停止，需要继续人工适配。详见[同步策略](docs/UPSTREAM_SYNC.md)。

[E01 实现与验证边界](docs/ECARX-E01-ANDROID51.md) · [保留的原仓库中文说明](docs/CARLITO-README.zh-CN.md) · [署名与许可证](docs/THIRD_PARTY_NOTICES.md)
