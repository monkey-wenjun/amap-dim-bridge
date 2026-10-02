package com.awen.amapdim;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 把导航信息推到仪表盘（DIM）。
 *
 * 车机（亿咖通 CS11）在 boot classpath 的 /system/framework/ecarx.adapterapi.jar 里预置了
 *
 *   com.ecarx.xui.adaptapi.diminteraction.DimInteraction
 *     .create(ctx).getNaviInteraction() -> INaviInteraction
 *
 * 原厂百度地图（com.baidu.naviauto）就是用它把转向指引推到仪表的。DiServer.onBind() 只看
 * Intent extra "app_type"（com.ecarx.navigation），不校验调用方包名/权限，所以第三方 App
 * 反射调用即可，编译期不需要那个 jar。
 *
 * INaviInteraction 的方法（来自 ecarx.adapterapi.jar）：
 *   notifyTurnByTurnStarted() / notifyTurnByTurnStopped()
 *   updateTurnByTurnArrow(int) / (Bitmap)
 *   updateNextGuidancePointName(String)
 *   updateDistanceToNextGuidancePoint(int)   // 米
 *   updateDistanceToDestination(int)         // 米
 *   updateETA(int)                           // 秒
 *   updateDriveDirection(int)
 *   notifyDestinationReached(String, int) / (String, Bitmap)
 */
public final class DimNavi {

    private static final String TAG = "DimNavi";
    private static final String PKG = "com.ecarx.xui.adaptapi.diminteraction.";

    /** 所有 IPC 放单线程队列，别占主线程（车机 CPU 弱） */
    private static final ExecutorService Q = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context appContext;
    private static Object dim;          // DimInteraction
    private static Class<?> dimCls;
    private static Object navi;         // INaviInteraction
    private static Class<?> naviCls;
    private static boolean apiMissing;  // 车机上没有这套类（模拟器）才置位
    private static int retry;
    private static volatile String status = "未连接";
    private static volatile String lastError = "";

    private DimNavi() {
    }

    public static String status() {
        return status;
    }

    public static String lastError() {
        return lastError;
    }

    private static final Runnable RETRY_TASK = new Runnable() {
        @Override
        public void run() {
            if (navi != null || apiMissing || retry++ >= 30) {
                return;
            }
            if (ensure()) {
                return;
            }
            MAIN.postDelayed(this, 1000);
        }
    };

    /** 服务 bind 是异步的：第一次拿不到就每秒重试，最多 30 次 */
    public static void ensureAsync(final Context ctx) {
        if (appContext == null) {
            appContext = ctx.getApplicationContext();
            if (appContext == null) {
                appContext = ctx;
            }
        }
        if (navi != null || apiMissing) {
            return;
        }
        Q.execute(new Runnable() {
            @Override
            public void run() {
                if (!ensure()) {
                    retry = 0;
                    MAIN.removeCallbacks(RETRY_TASK);
                    MAIN.postDelayed(RETRY_TASK, 1000);
                }
            }
        });
    }

    private static boolean ensure() {
        if (apiMissing) {
            return false;
        }
        if (navi != null) {
            return true;
        }
        synchronized (DimNavi.class) {
            if (apiMissing) {
                return false;
            }
            if (navi != null) {
                return true;
            }
            try {
                if (dim == null) {
                    dimCls = Class.forName(PKG + "DimInteraction");
                    naviCls = Class.forName(PKG + "INaviInteraction");
                    dim = dimCls.getMethod("create", Context.class).invoke(null, appContext);
                    if (dim == null) {
                        throw new IllegalStateException("DimInteraction.create() == null");
                    }
                }
                Object n = dimCls.getMethod("getNaviInteraction").invoke(dim);
                if (n == null) {
                    // 服务还在 bind（onServiceConnected 未回调）
                    status = "绑定仪表服务中…";
                    return false;
                }
                navi = n;
                status = "已连接仪表";
                Log.i(TAG, "仪表导航接口就绪");
                return true;
            } catch (ClassNotFoundException e) {
                apiMissing = true;
                status = "车机没有 DimInteraction API";
                Log.w(TAG, "车机没有仪表盘 API（模拟器？）：" + e);
                return false;
            } catch (NoSuchMethodException e) {
                apiMissing = true;
                status = "DimInteraction API 与预期不符";
                Log.w(TAG, "仪表盘 API 与预期不符：" + e);
                return false;
            } catch (Throwable t) {
                status = "连接失败，重试中";
                Log.w(TAG, "连接仪表服务失败：" + t);
                return false;
            }
        }
    }

    private static void call(final String method, final Class<?>[] sig, final Object... args) {
        Q.execute(new Runnable() {
            @Override
            public void run() {
        if (!ensure()) {
                    return;
                }
                try {
                    Method m = naviCls.getMethod(method, sig);
                    m.invoke(navi, args);
                } catch (Throwable t) {
                    lastError = method + ": " + t;
                    Log.w(TAG, "推送仪表失败 " + method + ": " + t);
                }
            }
        });
    }

    /**
     * 注意：**不要**调 notifyTurnByTurnStarted()。
     * 客户端 SDK 会把它转成 turnState=0，而服务端 NavigationDimInteraction.turnByTurn()
     * 明确要求 turnState != 0，否则抛 "Navigation state is not correct."
     * （实测日志：E/JavaBinder java.lang.RuntimeException: Navigation state is not correct.）
     * 仪表是在收到 turnState=1 的导航帧时进入导航界面的。
     */
    public static void stop() {
        call("notifyTurnByTurnStopped", new Class<?>[]{});
    }

    /** 转向箭头：ecarx 的 E_ICON_NAVI_* 图标码（见 AmapIcon） */
    public static void arrow(int iconCode) {
        call("updateTurnByTurnArrow", new Class<?>[]{int.class}, iconCode);
    }

    /** 下一个路口/路名 */
    public static void nextRoad(String name) {
        if (name == null) {
            return;
        }
        call("updateNextGuidancePointName", new Class<?>[]{String.class}, name);
    }

    /** 距下一个引导点（米） */
    public static void distanceToNext(int meters) {
        call("updateDistanceToNextGuidancePoint", new Class<?>[]{int.class}, meters);
    }

    /** 距终点（米） */
    public static void distanceToDest(int meters) {
        call("updateDistanceToDestination", new Class<?>[]{int.class}, meters);
    }

    /** 剩余时间（秒） */
    public static void eta(int seconds) {
        call("updateETA", new Class<?>[]{int.class}, seconds);
    }

    /** 车头方向 */
    public static void driveDirection(int direction) {
        call("updateDriveDirection", new Class<?>[]{int.class}, direction);
    }

    /** 到达目的地 */
    public static void arrived(String name) {
        call("notifyDestinationReached", new Class<?>[]{String.class, int.class},
                name == null ? "" : name, 0);
    }
}
