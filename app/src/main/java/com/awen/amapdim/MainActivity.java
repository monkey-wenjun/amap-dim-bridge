package com.awen.amapdim;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 简单的状态页：显示仪表连接状态、收到的广播日志，并控制桥接服务 */
public class MainActivity extends Activity {

    private TextView status;
    private TextView log;
    private Button toggle;
    private final Handler h = new Handler();

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            status.setText("仪表：" + DimNavi.status()
                    + (DimNavi.lastError().length() > 0 ? "  (" + DimNavi.lastError() + ")" : "")
                    + "   推送：" + (BridgeService.pushEnabled ? "开" : "关"));
            log.setText(BridgeService.logText());
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(build());
        startService(new Intent(this, BridgeService.class));
        h.post(refresh);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(refresh);
        super.onDestroy();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F5F7"));
        root.setPadding(32, 24, 32, 24);

        TextView title = new TextView(this);
        title.setText("高德 → 仪表盘 桥接");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#1B1E23"));
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.parseColor("#33373D"));
        status.setPadding(0, 12, 0, 12);
        root.addView(status);

        toggle = new Button(this);
        toggle.setText("开 / 关 仪表推送");
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, BridgeService.class)
                        .setAction(BridgeService.ACTION_TOGGLE));
            }
        });
        root.addView(toggle);

        TextView hint = new TextView(this);
        hint.setText("在高德车机版里开始导航，下面会实时显示收到的广播；"
                + "日志也写在 /sdcard/Android/data/com.awen.amapdim/files/probe.log");
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#7A8089"));
        hint.setPadding(0, 12, 0, 12);
        root.addView(hint);

        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        log = new TextView(this);
        log.setTextSize(12);
        log.setTextColor(Color.parseColor("#2B2F36"));
        log.setGravity(Gravity.START);
        log.setTextIsSelectable(true);
        sc.addView(log);
        root.addView(sc);
        return root;
    }
}
