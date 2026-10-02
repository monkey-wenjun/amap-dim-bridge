package com.awen.amapdim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 只在 manifest 里注册高德广播，作用是「保活」：进程被杀后，下一条高德广播
 * 会把 BridgeService 拉起来（真正的解析在服务里那个运行时接收器）。
 */
public class NavReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (BridgeService.ACTION_SET_DEFAULT_AMAP.equals(intent.getAction())) {
            DuerosNavi.setDefaultAmap(context);
            return;
        }
        context.startService(new Intent(context, BridgeService.class));
    }
}
