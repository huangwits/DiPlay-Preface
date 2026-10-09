# 发布规范

供维护者发布版本时参考。

- Release 只上传经过验证的正式 APK；源码、标签和许可证保留在仓库，不额外上传重复源码包。
- 标签为 `v<版本号>`，附件为 `DiPlay-Preface-v<版本号>.apk`。版本号与 APK 内部信息一致，沿用既有签名；编号规则见 [VERSIONING.md](VERSIONING.md)。
- 发布前完成 [BUILD.md](BUILD.md) 中的构建与验证步骤，核对版本、签名和文件哈希。
- 发布介绍只说明用户可见变化、适用设备和安装方法，不加入后台地址、管理操作、部署流程、内部变体名、混淆实现或测试流水账。
- README 和下载页保持简洁，不把维护过程或对话中的解释写成产品介绍。
- 安装签名私钥、授权签发私钥、管理员凭据、数据库和调试映射文件不得进入 APK 或公开附件。运行组件来源见 [第三方声明](THIRD_PARTY_NOTICES.md)。
- 保留原作者署名、对应源码和许可证；历史版本不随新发布静默改写或删除。

## English

Upload only the validated release APK. Keep corresponding source, tags, licenses and attribution in the repository. Match the tag, APK filename and internal version, preserve the signer and complete the build checks.

Release notes and introductory pages should describe user-visible changes and usage. Omit administration, deployment, implementation detail and maintenance history. Keep signing/issuer keys, credentials, databases and debug mappings private. Preserve historical releases.
