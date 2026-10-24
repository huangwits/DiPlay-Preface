# 安全说明

[返回项目首页](README.md) · [文档目录](docs/README.md)

## 问题报告

普通安装、连接和功能问题请通过 [问题反馈入口](https://github.com/huangwits/DiPlay-Preface/issues/new/choose) 提交。

涉及凭据或安全缺陷时，请先查看仓库 [Security 页面](https://github.com/huangwits/DiPlay-Preface/security) 是否提供私密报告入口。不要在公开 Issue 中上传密码、私钥、配对记录或其他敏感信息。

共享诊断报告前，请检查其中的设备信息、网络标识和个人信息。报告中应保留排查所需的版本、故障时间和错误上下文；日志导出方法见 [问题反馈](docs/USAGE.md#问题反馈)。

## 认证数据与签名

公开 APK 包含 [第三方声明](docs/THIRD_PARTY_NOTICES.md#experimental-authentication-data) 所述的实验性配件认证证书与配套密钥。持有 APK 的人可以提取这些数据；混淆、源码撤回或历史改写不能使已打包的共享密钥保持秘密，也不能撤销已取得的副本。

Android APK 签名密钥与上述配件认证数据不同。签名密钥保留在本地，不随 APK 分发。实验性认证数据当前能否使用，不代表 Apple 认证，也不保证未来兼容。

## 公开仓库范围

不要将管理员令牌、签发私钥、Android 签名密钥、配对凭据或本地运行数据提交到公开仓库。

当前公开树的内容与构建限制见 [源码说明](SOURCE_SCOPE.md)。
