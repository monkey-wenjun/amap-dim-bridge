package com.awen.amapdim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机（含车机自定义的 FIRST_BOOT_COMPLETED）自动拉起桥接服务 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        BridgeService.note("开机广播：" + intent.getAction());
        context.startService(new Intent(context, BridgeService.class));
    }
}
