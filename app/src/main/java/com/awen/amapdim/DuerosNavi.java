package com.awen.amapdim;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

/**
 * 把车机语音（百度 DuerOS / CoDriver）的**默认导航应用**改成高德。
 *
 * 取证（2026-10-02，见 tools/dueros-sdk/sdk-notes.txt）：
 *
 *   车机 DuerOS 开放 SDK 客户端（ecarx.voiceservice 内嵌 com.baidu.che.codriversdk）
 *   走 AIDL：com.baidu.che.codriver.ICoDriverService
 *       String sendCommand(String pkg, String cmd, String param, String data)   事务码 1
 *   cmd = 工具名（导航是 "map.tool"），param = 功能名，data = 数据。
 *
 *   服务端 com.baidu.che.codriver.sdk.handler.i（MapControlHandler）收到
 *   param="fun_navi_set_default_app" 时执行：
 *       new NaviPreferences().saveDefaultNaviApp(NaviApp.valueOf(data))
 *   NaviApp = { Baidu, Amap_Lite, Amap }，高德车机版 = "Amap"。
 *   偏好落在 /data/data/com.baidu.che.codriver/shared_prefs/prefs_amap.xml（无 root 直接改不了）。
 *
 * 这里直接 bind DuerOS 的 CoDriverService 自己发这条 Binder 事务——不用把 SDK 那 3.3MB dex
 * 塞进 App（4.4 是 Dalvik，也没有 multi-dex 可用）。
 */
public final class DuerosNavi {

    private static final String TAG = "DuerosNavi";
    private static final String TARGET_PKG = "com.baidu.che.codriver";
    private static final String TARGET_SVC = "com.baidu.che.codriver.sdk.CoDriverService";
    private static final String DESCRIPTOR = "com.baidu.che.codriver.ICoDriverService";
    private static final int TX_SEND_COMMAND = 1;

    private DuerosNavi() {
    }

    /** 把默认导航应用设成高德车机版（DuerOS 内部值 "Amap"） */
    public static void setDefaultAmap(Context ctx) {
        final Context app = ctx.getApplicationContext() != null
                ? ctx.getApplicationContext() : ctx;
        Intent i = new Intent();
        i.setComponent(new ComponentName(TARGET_PKG, TARGET_SVC));
        boolean ok = app.bindService(i, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    String r = sendCommand(binder, app.getPackageName(),
                            "map.tool", "fun_navi_set_default_app", "Amap");
                    BridgeService.note("✅ 默认导航已设为「高德」(Amap)，服务端返回：" + r);
                } catch (Throwable t) {
                    BridgeService.note("❌ 设置默认导航失败：" + t);
                    Log.w(TAG, "sendCommand 失败", t);
                } finally {
                    try {
                        app.unbindService(this);
                    } catch (Throwable ignored) {
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
        BridgeService.note(ok ? "已发起 bind DuerOS CoDriverService…"
                : "❌ bindService 失败：DuerOS 服务不可用");
    }

    private static String sendCommand(IBinder binder, String pkg, String cmd,
                                      String param, String data) throws Exception {
        Parcel out = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            out.writeInterfaceToken(DESCRIPTOR);
            out.writeString(pkg);
            out.writeString(cmd);
            out.writeString(param);
            out.writeString(data);
            binder.transact(TX_SEND_COMMAND, out, reply, 0);
            reply.readException();
            return reply.readString();
        } finally {
            out.recycle();
            reply.recycle();
        }
    }
}
