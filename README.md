# 高德仪表桥（amap-dim-bridge）

把**高德地图车机版**（`com.autonavi.amapauto`）的导航指引推到**领克 CS11 车机的仪表盘**。

车机（亿咖通 ECARX，Android 4.4.3）在 boot classpath 的
`/system/framework/ecarx.adapterapi.jar` 里预置了仪表交互门面
`com.ecarx.xui.adaptapi.diminteraction.DimInteraction`，原厂**百度地图**就是用它把
转向指引送到仪表的。这套 API 没有签名/权限校验（`DiServer.onBind()` 只看 Intent extra
`app_type=com.ecarx.navigation`），所以第三方 App 也能用。

高德自己不会调这套 API，但高德车机版会持续发标准广播
`AUTONAVI_STANDARD_BROADCAST_SEND`（`KEY_TYPE=10001` 带转向/路名/距离/时间）。
本 App 就是这两者之间的桥：

```
高德车机版 --广播(AUTONAVI_STANDARD_BROADCAST_SEND, KEY_TYPE=10001)--> 本 App
                                                                        |
               DimInteraction.create(ctx).getNaviInteraction()          |
               notifyTurnByTurnStopped / updateTurnByTurnArrow /        |
               updateNextGuidancePointName / updateDistanceTo* /        v
               updateETA / notifyDestinationReached            com.ecarx.dim_interaction
                                                                    （DiServer -> 仪表 MCU）
```

## 构建 / 安装

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=$HOME/.local/share/mise/installs/java/temurin-17.0.20+101
./gradlew --offline :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

装完点一次图标即可（也可以不管：开机或下一条高德广播会自动拉起服务）。
界面上能看到「仪表：已连接仪表」和实时收到的高德广播。

## 已实测（2026-10-02，CS11 真车）

- 仪表接口绑定成功：`DimInteractionImpl.onServiceConnected() -> NaviInteractionImpl()`
- 高德开始导航后广播：`KEY_TYPE=10001 ICON=3 SEG=20 ROUTE_DIS=1270436 ROUTE_TIME=46725 NEXT=红军营东路`
- 仪表服务收到并出帧：
  `NavigationDimInteraction: 117 红军营东路 1270436 46725 null 20` ->
  `FrameBuilder: payload.length:105` -> `FrameSender: sending foreground counter = 21`
- 退出导航后 10001 停发，看门狗 20s 后自动清仪表（`notifyTurnByTurnStopped`）

## 两个坑（都已在代码里绕开）

1. **不要调 `notifyTurnByTurnStarted()`**。客户端 SDK 会把它设成 `turnState=0`，
   而服务端 `NavigationDimInteraction.turnByTurn()` 在 `turnState==0` 时直接抛
   `RuntimeException: Navigation state is not correct.`（仪表靠 `turnState=1` 的帧进导航界面）。
2. **`updateDriveDirection(int)` 在这个 SDK 版本里是空实现**（直接 `return-void`），不用调。

详见 `docs/仪表盘-导航.md` 和 `docs/图标码表.md`。
