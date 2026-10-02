package com.awen.amapdim;

/**
 * 高德车机版广播里的 ICON（转向图标 id）→ 仪表认的 arrowId。
 *
 * ⚠️ 仪表认的不是 `XCDimInteractionService.apk` 里那套 `E_ICON_NAVI_*`（112~138）常量——
 * 那是车机自己「联动图片」的 id，发过去仪表会当成别的图标（实测把「左转」显示成了「大转盘」）。
 *
 * 权威值域取自**原厂百度地图**：`Dueros-BaiduMapAuto.apk` 的 `com.baidu.a.a`
 * （NaviDashboardManager）里有一张 `HashMap<百度图标名, arrowId>`，它填进
 * `INaviInteraction.updateTurnByTurnArrow(arrowId)`，在真车仪表上显示是正确的：
 *
 *   turn_start                     101
 *   turn_left  (+2/3岔的左变体)     102      ← 左转
 *   turn_right (+2/3岔的右变体)     103      ← 右转
 *   turn_left_front / _side / branch_left 等  104   ← 左前方
 *   turn_right_front / _side / branch_right 等 105  ← 右前方
 *   turn_left_back                 106      ← 左后方
 *   turn_right_back                107      ← 右后方
 *   turn_back (+变体)              108      ← 掉头
 *   turn_front / branch_center / *_side_main 等 109  ← 直行
 *   turn_via_1..4                  110
 *   turn_ring                      111      ← 环岛
 *   turn_ring_out / _front         112      ← 环岛出口/前方
 *   turn_tollgate                  114      ← 收费站
 *   turn_dest                      115      ← 到达
 *   turn_along                     120      ← 沿路直行
 *   turn_ring_left / leftback / leftfront   141  ← 环岛左
 *   turn_ring_right / rightback / rightfront 142 ← 环岛右
 *   turn_ring_back                 143      ← 环岛掉头
 *
 * 高德那侧 id（= `res/drawable-hdpi-v4/navistate_landback_<十六进制 id>.png`）含义见
 * docs/图标码表.md，其中 id=3=右转 是实车验证过的。
 */
public final class AmapIcon {

    // 仪表 arrowId（百度地图同款值域）
    public static final int A_START = 101;
    public static final int A_LEFT = 102;
    public static final int A_RIGHT = 103;
    public static final int A_LEFT_FRONT = 104;
    public static final int A_RIGHT_FRONT = 105;
    public static final int A_LEFT_BACK = 106;
    public static final int A_RIGHT_BACK = 107;
    public static final int A_TURN_BACK = 108;
    public static final int A_STRAIGHT = 109;
    public static final int A_VIA = 110;
    public static final int A_ROUND = 111;
    public static final int A_ROUND_OUT = 112;
    public static final int A_TOLL = 114;
    public static final int A_DEST = 115;
    public static final int A_ALONG = 120;
    public static final int A_ROUND_LEFT = 141;
    public static final int A_ROUND_RIGHT = 142;
    public static final int A_ROUND_BACK = 143;

    private AmapIcon() {
    }

    /** 高德 ICON id → 仪表 arrowId */
    public static int toCluster(int amapIcon) {
        switch (amapIcon) {
            case 0:   // 直行
                return A_STRAIGHT;
            case 1:   // 左转
                return A_LEFT;
            case 2:   // 直行+左（左前方）
                return A_LEFT_FRONT;
            case 3:   // 右转（实车验证）
                return A_RIGHT;
            case 4:   // 直行+右（右前方）
                return A_RIGHT_FRONT;
            case 5:   // 掉头
            case 8:   // 右掉头
            case 14:  // 复合（掉头）
                return A_TURN_BACK;
            case 6:   // 前方左右
            case 7:   // 十字路口
            case 13:  // 直行（带汇入提示）
            case 15:  // 公交专用道
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:  // 公交专用道
            case 23:  // 可变车道
                return A_STRAIGHT;
            case 9:   // 直行+右掉头
                return A_RIGHT_FRONT;
            case 10:  // 直行+左掉头
                return A_LEFT_FRONT;
            case 11:  // 复合路口（左）
                return A_LEFT_FRONT;
            case 12:  // 复合路口（右）
                return A_RIGHT_FRONT;
            default:
                return A_STRAIGHT;
        }
    }
}
