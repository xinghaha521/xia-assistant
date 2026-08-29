# 小a 节点服务

Android 端**屏幕节点读取服务**，通过 LocalSocket 实时推送全屏 XML 给客户端（主程序）。

---

## 架构定位

```
┌─────────────────────┐  LocalSocket (Unix Domain)  ┌──────────────┐
│  本应用【小a】       │ ◄──────────────────────────► │ 【主程序】     │
│  AccessibilityService│     XML 节点推送            │  EC / HID    │
│  + 前台服务保活      │                             │  点击执行端   │
└─────────────────────┘                              └──────────────┘
```

- **运行身份**：无障碍服务 + 前台服务（**非**"数字助理"身份，避开小米白名单）
- **通信方式**：`LocalServerSocket("aifriend_assistant")`
- **消息格式**：`[4 字节大端长度][UTF-8 消息体]`，消息体为 `<dump>...</dump>` 或 `<ping/>`

---

## 客户端测试（PC 端）

### 方式一：LocalSocket 直接连（设备已 root）
```bash
adb shell nc -U aifriend_assistant
```

### 方式二：adb forward 端口（推荐，无需 root）
```bash
adb forward tcp:9000 localabstract:aifriend_assistant
nc localhost 9000
```

---

## 编译（GitHub Actions 自动）

1. 推送代码到 GitHub 仓库（任意分支）
2. 进入 Actions 页面查看构建日志
3. 构建完成后在 Artifacts 下载 `xia-debug-apk`

**手动触发**：
- 进入 Actions → Build APK → Run workflow

---

## 安装与运行

### 1. 安装 APK
```bash
adb install app-debug.apk
```

### 2. 开启无障碍服务
设置 → 无障碍 → 找到 **【小a】节点服务** → 开启

### 3. 开启权限（首次）
应用内：
- ① 无障碍服务 → "去开启"
- ② 自启动权限 → "去开启" → 设置 → 应用管理 → 小a → 自启动
- ③ 电池优化 → "去申请电池白名单"

### 4. 启动前台服务
应用内点击 **"启动前台服务"** 按钮
（前台服务会显示一个常驻通知，并提升进程优先级）

---

## 调试清单

| 检查项 | 期望 | 命令 |
|--------|------|------|
| 无障碍服务 | 已开启 | 应用主页面顶部状态 |
| 前台服务 | 通知栏有通知 | 下拉通知栏 |
| LocalSocket 监听 | adb 可连接 | `adb shell netstat -l \| grep aifriend_assistant` |
| 节点数据 | 微信打开后，调试页"最后包大小" > 1KB | 主页面调试区域 |
| 客户端连通 | `nc` 能看到 `<dump>` 流 | `adb forward` + `nc` |

---

## 目录结构

```
xia/
├── .github/workflows/build.yml    # 自动编译
├── app/
│   ├── build.gradle.kts            # app 模块
│   └── src/main/
│       ├── AndroidManifest.xml     # 权限 + 服务声明
│       ├── java/com/aifriend/assistant/
│       │   ├── XiaApp.kt           # Application
│       │   ├── MainActivity.kt     # 引导 + 调试页
│       │   ├── NodeService.kt      # 核心无障碍服务
│       │   ├── NodeDumper.kt       # XML dump 工具
│       │   ├── NodePusher.kt       # LocalSocket 服务端
│       │   ├── AssistForegroundService.kt  # 前台保活
│       │   ├── BootReceiver.kt     # 开机自启
│       │   └── DebugViewModel.kt   # 调试状态
│       └── res/
│           ├── layout/activity_main.xml
│           ├── xml/accessibility_config.xml
│           └── ...
├── build.gradle.kts                # 顶层
├── settings.gradle.kts
└── gradle.properties
```

---

## 后续规划

- [x] P0：基础无障碍服务 + LocalSocket 推送
- [ ] P1：保活机制完善（前台服务 + 自启动 + 电池白名单）— 当前已完成基础
- [ ] P2：【主程序】Socket 客户端 + XML 解析 + lockNode
- [ ] P3：双 APK 联调，OTG HID 点击闭环
- [ ] P4：蓝牙 HID 模式适配（ESP32-C3）

---

## 已知坑 & 风险

⚠️ **MIUI 12.5 后台管控极严**
- 必须手动开启：自启动 + 电池白名单 + 无障碍
- 即便全部开启，仍可能被杀，建议挂前台服务+锁屏前确保无障碍在运行

⚠️ **USB Host 抢占**
- 主程序占用 USB 时，【小a】可能被冻结
- 解决方案：主程序用完 USB 后立即释放，并重启【小a】Service

⚠️ **AccessibilityService 被杀后无法自启**
- MIUI 自启动白名单是必要条件
- Android 8+ `startForegroundService` 比 `startService` 更可靠