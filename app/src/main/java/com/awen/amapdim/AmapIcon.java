package com.awen.amapdim;

/**
 * 高德车机版广播里的 ICON（转向图标 id）→ 亿咖通仪表 E_ICON_NAVI_* 图标码。
 *
 * 仪表端图标码：取自 /system/app/XCDimInteractionService.apk 的
 * com.ecarx.dim_interaction.template.dimscreen.LinkedImageMapping 字段值：
 *
 *   E_ICON_NAVI=112  STRAIGHT=113  TURN_BACK=114  TURN_R_T=115  TURN_L_T=116
 *   TURN_R=117       TURN_L=118    TURN_R_D=119  TURN_L_D=120  KEEP_R=121
 *   KEEP_L=122       EXIT_R=123    EXIT_L=124    ROUND=125      TOLL=126
 *   DEST=127         FERRY=128     ENTRY_R=133   ENTRY_L=134    JCT=135
 *   SA=137           TUNNEL=138
 *
 * 高德端 id：就是高德车机版自带资源 res/drawable-hdpi-v4/navistate_landback_&lt;id 的十六进制&gt;.png。
 * 已实车验证 id=3 = 右转（2026-10-02，CS11，导航到「红军营东路」）。完整对照见
 * docs/图标码表.md。没把握的复杂路口一律回落到通用导航图标，宁可少画不要画错。
 */
public final class AmapIcon {

    // 仪表（ecarx）图标码
    public static final int E_NAVI = 112;
    public static final int E_STRAIGHT = 113;
    public static final int E_TURN_BACK = 114;
    public static final int E_TURN_R_T = 115;
    public static final int E_TURN_L_T = 116;
    public static final int E_TURN_R = 117;
    public static final int E_TURN_L = 118;
    public static final int E_TURN_R_D = 119;
    public static final int E_TURN_L_D = 120;
    public static final int E_KEEP_R = 121;
    public static final int E_KEEP_L = 122;
    public static final int E_EXIT_R = 123;
    public static final int E_EXIT_L = 124;
    public static final int E_ROUND = 125;
    public static final int E_TOLL = 126;
    public static final int E_DEST = 127;
    public static final int E_FERRY = 128;
    public static final int E_JCT = 135;
    public static final int E_SA = 137;
    public static final int E_TUNNEL = 138;

    private AmapIcon() {
    }

    /** 高德 ICON id → 仪表图标码 */
    public static int toCluster(int amapIcon) {
        switch (amapIcon) {
            case 0:   // 直行
                return E_STRAIGHT;
            case 1:   // 左转
                return E_TURN_L;
            case 2:   // 直行 + 左（左前方）
                return E_TURN_L_T;
            case 3:   // 右转（实测验证）
                return E_TURN_R;
            case 4:   // 直行 + 右（右前方）
                return E_TURN_R_T;
            case 5:   // 掉头
            case 8:   // 右掉头
                return E_TURN_BACK;
            case 6:   // 前方左右
            case 7:   // 十字路口
            case 11:
            case 12:
            case 14:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
                return E_JCT;
            case 9:
            case 10:
                return E_TURN_R_T;
            case 13:  // 直行（带汇入提示）
                return E_STRAIGHT;
            case 21:  // 公交专用道
            case 23:  // 可变车道
            default:
                return E_NAVI;
        }
    }
}
