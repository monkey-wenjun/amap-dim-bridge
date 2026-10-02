package com.awen.amapdim;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 监听高德车机版的标准广播 AUTONAVI_STANDARD_BROADCAST_SEND，
 * 把导航状态转成亿咖通 DimInteraction 的 INaviInteraction 调用，推到仪表盘。
 */
public class BridgeService extends Service {

    public static final String TAG = "AmapDimBridge";
    public static final String ACTION_RELOAD = "com.awen.amapdim.RELOAD";
    public static final String ACTION_TOGGLE = "com.awen.amapdim.TOGGLE";

    /** 是否真的往仪表推（调试期可关掉，只看日志） */
    public static volatile boolean pushEnabled = true;
    /** 最近的事件（给界面显示） */
    private static final StringBuilder LOG = new StringBuilder();
    private static final int LOG_MAX = 20000;

    private BroadcastReceiver rx;
    private boolean naviActive;
    private int lastIcon = -1;

    public static String logText() {
        synchronized (LOG) {
            return LOG.toString();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "BridgeService onCreate");
        DimNavi.ensureAsync(this);
        register();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            pushEnabled = !pushEnabled;
            note("推送开关 = " + pushEnabled);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (rx != null) {
            try {
                unregisterReceiver(rx);
            } catch (Throwable ignored) {
            }
            rx = null;
        }
        super.onDestroy();
    }

    private void register() {
        if (rx != null) {
            return;
        }
        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    handle(intent);
                } catch (Throwable t) {
                    Log.w(TAG, "handle 出错", t);
                }
            }
        };
        IntentFilter f = new IntentFilter("AUTONAVI_STANDARD_BROADCAST_SEND");
        registerReceiver(rx, f);
        note("已注册高德广播接收器");
    }

    /** 解析一条高德广播 */
    private void handle(Intent it) {
        int keyType = it.getIntExtra("KEY_TYPE", -1);
        int state = it.getIntExtra("EXTRA_STATE", -1);

        String nextRoad = it.getStringExtra("NEXT_ROAD_NAME");
        if (nextRoad == null) {
            nextRoad = it.getStringExtra("CUR_ROAD_NAME");
        }
        int segDis = it.getIntExtra("SEG_REMAIN_DIS", -1);
        int routeDis = it.getIntExtra("ROUTE_REMAIN_DIS", -1);
        int routeTime = it.getIntExtra("ROUTE_REMAIN_TIME", -1);
        int icon = it.getIntExtra("ICON", -1);
        int newIcon = it.getIntExtra("NEW_ICON", -1);
        int direction = it.getIntExtra("CAR_DIRECTION", -1);
        int trafficLight = it.getIntExtra("TRAFFIC_LIGHT_NUM", -1);

        StringBuilder sb = new StringBuilder();
        sb.append("KEY_TYPE=").append(keyType)
                .append(" STATE=").append(state)
                .append(" ICON=").append(icon)
                .append(" NEW_ICON=").append(newIcon)
                .append(" SEG=").append(segDis)
                .append(" ROUTE_DIS=").append(routeDis)
                .append(" ROUTE_TIME=").append(routeTime)
                .append(" DIR=").append(direction)
                .append(" LIGHT=").append(trafficLight)
                .append(" NEXT=").append(nextRoad);
        note(sb.toString());

        // 完整 extras 落到文件，之后分析用
        dumpAll(it, keyType, state);

        if (!pushEnabled) {
            return;
        }

        switch (keyType) {
            case 10001: // 导航开始 / 进入导航
                naviActive = true;
                DimNavi.start();
                pushFields(icon, nextRoad, segDis, routeDis, routeTime, direction);
                break;
            case 10019: // 导航中信息
                if (state == 0) { // 退出导航
                    naviActive = false;
                    DimNavi.stop();
                } else {
                    if (!naviActive) {
                        naviActive = true;
                        DimNavi.start();
                    }
                    pushFields(icon, nextRoad, segDis, routeDis, routeTime, direction);
                }
                break;
            case 10021: // 到达/结束
                naviActive = false;
                DimNavi.arrived(nextRoad);
                DimNavi.stop();
                break;
            default:
                break;
        }
    }

    private void pushFields(int icon, String nextRoad, int segDis, int routeDis,
                            int routeTime, int direction) {
        if (nextRoad != null && nextRoad.length() > 0) {
            DimNavi.nextRoad(nextRoad);
        }
        if (segDis >= 0) {
            DimNavi.distanceToNext(segDis);
        }
        if (routeDis >= 0) {
            DimNavi.distanceToDest(routeDis);
        }
        if (routeTime >= 0) {
            DimNavi.eta(routeTime);
        }
        if (direction >= 0) {
            DimNavi.driveDirection(direction);
        }
        if (icon >= 0 && icon != lastIcon) {
            lastIcon = icon;
            DimNavi.arrow(AmapIcon.toCluster(icon));
        }
    }

    private void dumpAll(Intent it, int keyType, int state) {
        try {
            Bundle b = it.getExtras();
            if (b == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(ts()).append(" KEY_TYPE=").append(keyType)
                    .append(" STATE=").append(state);
            for (String k : b.keySet()) {
                Object v = b.get(k);
                sb.append(" | ").append(k).append('=').append(v);
            }
            sb.append('\n');
            File dir = getExternalFilesDir(null);
            if (dir == null) {
                return;
            }
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File f = new File(dir, "probe.log");
            if (f.exists() && f.length() > 2 * 1024 * 1024) {
                f.delete();
            }
            FileWriter w = new FileWriter(f, true);
            try {
                w.write(sb.toString());
            } finally {
                w.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "dumpAll 失败", t);
        }
    }

    static void note(String s) {
        String line = ts() + " " + s;
        Log.i(TAG, s);
        synchronized (LOG) {
            LOG.insert(0, line + "\n");
            if (LOG.length() > LOG_MAX) {
                LOG.setLength(LOG_MAX);
            }
        }
    }

    private static String ts() {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
