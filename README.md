# 高德仪表桥（amap-dim-bridge）

把**高德地图车机版**（`com.autonavi.amapauto`）的导航指引推到**领克 CS11 车机的仪表盘**。

车机是亿咖通（ECARX）方案、Android 4.4.3（API 19）。原厂只让**百度地图**往仪表盘投导航，
高德投不了。这个 App 就是中间的桥：一头听高德的标准广播，一头调亿咖通预置在 boot classpath
里的仪表交互门面。**不需要 root，不需要签名权限，不改动任何原厂应用。**

```
高德车机版 --广播(AUTONAVI_STANDARD_BROADCAST_SEND, KEY_TYPE=10001)--> 本 App
                                                                        |
               DimInteraction.create(ctx).getNaviInteraction()          |
               notifyTurnByTurnStopped / updateTurnByTurnArrow /        |
               updateNextGuidancePointName / updateDistanceTo* /        v
               updateETA / notifyDestinationReached            com.ecarx.dim_interaction
                                                                    （DiServer -> 仪表 MCU）
```

## 为什么能这么做

车机在 `/system/framework/ecarx.adapterapi.jar`（**boot classpath**，第三方 App 直接可见）里
预置了仪表交互门面：

```java
com.ecarx.xui.adaptapi.diminteraction.DimInteraction
    .create(Context).getNaviInteraction()   // -> INaviInteraction
```

原厂百度地图就是用它把转向指引送到仪表的。这套 API **没有签名/权限校验**——
`DiServer.onBind()` 只看 Intent extra `app_type=com.ecarx.navigation`，不校验调用方包名，
所以第三方 App 反射调用即可，编译期连那个 jar 都不需要。

高德自己不会调这套 API，但高德车机版会持续发标准广播
`AUTONAVI_STANDARD_BROADCAST_SEND`（`KEY_TYPE=10001` 带转向图标 / 路名 / 距离 / 剩余时间）。
本 App 把两者接起来。

完整链路、字段全量清单、服务端 `turnState` 状态机和取证过程见
[`docs/仪表盘-导航.md`](docs/仪表盘-导航.md)。

## 功能

| 功能 | 入口 | 说明 |
| --- | --- | --- |
| 导航投仪表 | 常驻前台服务，自动运行 | 转向箭头、下一路口路名、距下一路口、距终点、ETA、到达提示 |
| 改 DuerOS 默认导航为高德 | App 内按钮 / 广播 | 裸 Binder 调 `ICoDriverService.sendCommand`，**无需 root**，也不用把百度 SDK 打进 APK |
| 把高德拖进左侧栏（hotseat） | `tools/add-amap-to-hotseat.sh` | `sendevent` 合成长按拖拽手势，无需 root |

App 界面是个状态页：显示仪表连接状态、推送开关、实时收到的高德广播。
完整广播 extras 会落到 `/sdcard/Android/data/com.awen.amapdim/files/probe.log`（2MB 滚动），
方便你自己对照字段。

## 适用环境

实测环境，别的车机/版本大概率要改：

- **车型**：领克 CS11（`ro.product.model=CS11`），亿咖通 ECARX 车机
- **系统**：Android 4.4.3（API 19），Dalvik，i.MX6 / Cortex-A9（**armv7，装不了 arm64 包**）
- **屏幕**：`wm size` 被覆盖成 1280x720
- **高德**：车机版 V9.1.0.600087（arm32）
  - sha256 `2418635818cd3e3294cfaa66a1fcb2d94d3a903dee81956e399a960e67f07d9f`，签名 `CN=minimap, O=autonavi`（官方）
  - V9.5.0.600013 是 arm64，本车装不了
- **仪表服务**：`/system/app/XCDimInteractionService.apk`（`com.ecarx.dim_interaction`）
- **语音/DuerOS**：`com.baidu.che.codriver`，SDK 内嵌在 `/system/app/XCVoiceService.apk`

需要能连上车机的 adb（USB）。

## 构建 / 安装

依赖：JDK 17、Android SDK（`compileSdk 34`）、Gradle 8.10.2（wrapper 自带）。

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=<你的 JDK 17 路径>
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

或者直接跑封装好的一键脚本（会用 mise 里的 temurin-17 并自动拉起 App）：

```bash
./install-to-car.sh
```

装完点一次图标即可，之后不用管：开机广播或下一条高德广播会自动拉起服务。
界面上能看到「仪表：已连接仪表」和实时收到的高德广播。

> `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 指向腾讯云镜像。
> 海外构建请改回 `https://services.gradle.org/distributions/gradle-8.10.2-all.zip`。

### 签名

`release` 用的是仓库里的 `app/debug.keystore`（口令 `android` / 别名 `androiddebugkey`，
即 Android 默认调试口令，写在 `app/build.gradle`）。这样 fork 之后不用自己生成密钥就能直接编译安装。

代价：任何人 clone 都能签出**同签名**的 APK，可以静默覆盖你车上的这个应用。
介意的话把 `signingConfigs.release` 换成你自己的 keystore，并把 `app/debug.keystore`
从仓库里删掉（本应用不依赖任何 signature 级权限，换签名不影响功能）。

## 使用

```bash
# 起导航（不用点屏幕，高德认这个 scheme）
adb shell am start -a android.intent.action.VIEW \
  -d "androidauto://navi?sourceApplication=awen&lat=30.2741&lon=120.1551&poiname=test&dev=0&style=2"

# 退出导航
adb shell am start -a android.intent.action.VIEW -d "androidauto://naviExit"

# 把 DuerOS 默认导航改成高德（也可以点 App 里的按钮）
adb shell am broadcast -a com.awen.amapdim.SET_DEFAULT_AMAP

# 看日志
adb logcat -s AmapDimBridge DimNavi DuerosNavi NavigationDimInteraction FrameBuilder FrameSender
```

高德自己认的 URI scheme 只有 `androidauto://`，host 有 `navi` / `naviPreview` / `keywordNavi` /
`viewGeo` / `viewReGeo` / `naviExit` / `naviSetting` 等。

## 已实测（2026-10-02，CS11 真车）

- 仪表接口绑定成功：`DimInteractionImpl.onServiceConnected() -> NaviInteractionImpl()`
- 高德开始导航后广播：`KEY_TYPE=10001 ICON=3 SEG=20 ROUTE_DIS=1270436 ROUTE_TIME=46725 NEXT=红军营东路`
- 仪表服务收到并出帧：
  `NavigationDimInteraction: 117 红军营东路 1270436 46725 null 20` ->
  `FrameBuilder: payload.length:105` -> `FrameSender: sending foreground counter = 21`
- 退出导航后 `10001` 停发，看门狗 20s 后自动清仪表（`notifyTurnByTurnStopped`）

## 三个坑（都已在代码里绕开）

1. **不要调 `notifyTurnByTurnStarted()`**。客户端 SDK 会把它设成 `turnState=0`，
   而服务端 `NavigationDimInteraction.turnByTurn()` 在 `turnState==0` 时直接抛
   `RuntimeException: Navigation state is not correct.`（仪表靠 `turnState=1` 的帧进导航界面）。
2. **转向码别用 `E_ICON_NAVI_*`（112~138）**。那是车机自己「联动图片」的 id，
   发给仪表会显示成别的图标（实测把「左转」画成了「大转盘」）。仪表真正认的是**原厂百度地图
   那套 arrowId**（左转 102 / 右转 103 / 直行 109 …）。
   高德 id → arrowId 的映射在 `AmapIcon.java`，值域来源见 [`docs/图标码表.md`](docs/图标码表.md)。
3. **`updateDriveDirection(int)` 在这个 SDK 版本里是空实现**（直接 `return-void`），不用调。

## 项目结构

```
app/src/main/java/com/awen/amapdim/
├── BridgeService.java    前台服务：收高德广播、节流、看门狗、落 probe.log
├── DimNavi.java          反射调 DimInteraction/INaviInteraction，单线程队列 + 30 次重试
├── AmapIcon.java         高德 ICON id -> 仪表 arrowId 映射表
├── DuerosNavi.java       裸 Binder 调 ICoDriverService.sendCommand 改默认导航
├── NavReceiver.java      manifest 里监听高德广播，进程被杀后保活拉起
├── BootReceiver.java     开机自启（含车机自定义 FIRST_BOOT_COMPLETED）
└── MainActivity.java     状态页（纯代码构建布局，无 XML）

docs/
├── 仪表盘-导航.md         完整链路、广播字段全量、turnState 状态机、取证与复现步骤
├── 图标码表.md            高德 ICON id 对照表 + 仪表真正认的 arrowId 值域
└── 左侧栏-百度地图.md      hotseat 是 launcher.db 固定记录、为什么 pm block 会留后遗症

tools/
├── add-amap-to-hotseat.sh   sendevent 合成拖拽，把高德图标拖进左侧栏
└── dueros-sdk/              DuerOS SDK 的反汇编笔记（见下）
```

节流策略：高德每秒推好几条，而仪表不需要这么快——**有变化立刻推，没变化最多 1 秒一帧**
（`BridgeService.pushFields()`）。所有 IPC 走单线程 `ExecutorService`，不占主线程（车机 CPU 弱）。

### tools/dueros-sdk/

只有 `sdk-notes.txt`：对 `com.baidu.che.codriversdk` 的反汇编笔记（`RequestManager` /
`PlatformService` / `CdNaviManager` 的字段、方法签名、`sendRequest` 调用点），
`DuerosNavi.java` 里那条 Binder 事务就是照它写出来的。

**原始 dex 不在仓库里，构建也不需要**——`app/build.gradle` 的 `dependencies` 是空的，
`DuerosNavi` 直接发裸 Binder 事务而不是调 SDK（4.4 是 Dalvik，没法把那 3.4MB 的 dex
塞进 App，也没有 multi-dex 可用）。那份 dex 是百度 DuerOS 的私有二进制，版权归原权利人，
不随本仓库分发。要自己复现取证，从车机里抽：

```bash
adb pull /system/app/XCVoiceService.apk
unzip -l XCVoiceService.apk | grep -i '\.dex'   # 看清路径再解
```

## 已知限制

- 只在领克 CS11 上验证过。换车型/换亿咖通版本，`DimInteraction` 的类名和方法签名可能不一样
  （`DimNavi` 会把 `ClassNotFoundException` / `NoSuchMethodException` 显示在状态页上）。
- 高德 id → arrowId 的映射里，**只有 `id=3`（右转）是实车验证过的**，其余按图标形状推断。
  仪表上图标不对就照 `docs/图标码表.md` 改 `AmapIcon.java` 的数。
- `KEY_TYPE=10019` 的 `EXTRA_STATE` 是几十到几百的状态码（不是前台/后台枚举），本桥不依赖它。
- 左侧栏（hotseat）那条记录一旦被 Launcher3 删掉，**不 root 就加不回来**，只能靠拖拽。
  踩坑经过见 `docs/左侧栏-百度地图.md`。

## 免责声明

本项目是个人对自己车辆的改装实验，通过反射/Binder 调用车机上未公开的系统接口。
使用它可能违反车辆或车机软件的保修/服务条款，也可能影响行车安全。
**请仅在停车状态下调试，自行承担全部风险。** 与领克、亿咖通、高德、百度均无关联，未获其授权。

## 许可证

[Apache License 2.0](LICENSE) — Copyright 2026 monkey-wenjun

本许可覆盖本仓库自己的代码与文档。仓库内**不含任何第三方二进制**；
`tools/dueros-sdk/sdk-notes.txt` 是对百度 DuerOS SDK 的反汇编分析笔记，
其中引用的类名/方法签名版权归原权利人所有。
