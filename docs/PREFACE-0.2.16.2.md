# DiPlay 星瑞 0.2.16.2

- 改善 USB 数据帧处理，兼容部分设备省略或延后发送补零字节的情况。
- 系统无法打开 VPN 授权时显示提示，支持返回后重试。
- 关闭未修改的设置时减少多余的画面协商；离开有修改的设置时，可保存、放弃或继续编辑。
- 优化缓冲音乐的读取节奏和音频缓冲释放，降低集中读取对同一连接上音视频的影响。
- 调整软件麦克风编码的语音配置，并在连接前检测编码能力。
- 补充旧安卓授权连接所需的根证书，改善部分 E01 的证书不受信任错误；日期或证书异常时显示中文提示。
- 保留应用内检查更新、USB 和现有无线连接功能。

适用于星瑞 E01，Android 5.1 起。请停车并结束 CarPlay 和蓝牙维护后覆盖安装，保留原有设置及授权信息。本版为上游功能的选择性适配，实际连接和音质仍需在车机上确认。

## English

- Improved USB frame parsing when optional padding is absent or arrives later.
- Added a retryable message when the system cannot open VPN authorization.
- Avoided unnecessary display negotiation when closing unchanged settings; leaving edited settings offers save, discard or continue editing.
- Paced buffered music intake and released decoder buffers before playback writes.
- Tuned software microphone encoding for speech and checked encoder availability before connection.
- Added public root certificates for license connections on older Android systems, with clearer messages for certificate and date errors.
- Retained in-app updates, USB and existing wireless connection features.

For Geely E01 running Android 5.1 or newer. Park and end CarPlay and Bluetooth maintenance before installing over the existing app to retain settings and activation information. This selectively adapts upstream changes; vehicle connectivity and audio quality still need on-device confirmation.
