package com.awen.amapdim;

/**
 * 高德车机版广播里的 ICON（转向图标 id）→ 亿咖通仪表 E_ICON_NAVI_* 图标码。
 *
 * 仪表端的图标码取自 /system/app/XCDimInteractionService.apk 里
 * com.ecarx.dim_interaction.template.dimscreen.LinkedImageMapping 的字段值：
 *
 *   E_ICON_NAVI=112  STRAIGHT=113  TURN_BACK=114  TURN_R_T=115  TURN_L_T=116
 *   TURN_R=117       TURN_L=118    TURN_R_D=119  TURN_L_D=120  KEEP_R=121
 *   KEEP_L=122       EXIT_R=123    EXIT_L=124    ROUND=125      TOLL=126
 *   DEST=127         FERRY=128     DEST_1..5=129..132,136  ENTRY_R=133  ENTRY_L=134
 *   JCT=135          SA=137        TUNNEL=138
 *
 * 高德 ICON 的取值语义先用实测（AmapProbe 记录）反推，未识别的回落到「直行」。
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

    /** 高德 ICON id → 仪表图标码（实测前先用保守映射） */
    public static int toCluster(int amapIcon) {
        switch (amapIcon) {
            case 0:
                return E_NAVI;
            case 1:
                return E_TURN_L;
            case 2:
                return E_TURN_R;
            case 3:
                return E_TURN_L_T;
            case 4:
                return E_TURN_R_T;
            case 5:
                return E_TURN_L_D;
            case 6:
                return E_TURN_R_D;
            case 7:
            case 8:
                return E_TURN_BACK;
            case 9:
                return E_STRAIGHT;
            case 10:
                return E_ROUND;
            case 11:
                return E_ROUND;
            case 12:
                return E_KEEP_L;
            case 13:
                return E_KEEP_R;
            case 14:
                return E_FERRY;
            case 15:
                return E_TOLL;
            case 16:
                return E_TUNNEL;
            case 17:
                return E_JCT;
            case 18:
                return E_SA;
            case 19:
                return E_EXIT_L;
            case 20:
                return E_EXIT_R;
            default:
                return E_STRAIGHT;
        }
    }
}
