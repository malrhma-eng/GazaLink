package com.gazalink.remote;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class RemoteControlService extends AccessibilityService {
    public static RemoteControlService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        MqttBackgroundService.sendToPC("{\"type\":\"STATUS\",\"text\":\"🟢 خدمة التحكم باللمس مفعلة وجاهزة\"}");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public void performTap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
        dispatchGesture(builder.build(), null, null);
    }

    public void performSwipe(int x1, int y1, int x2, int y2) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 350));
        dispatchGesture(builder.build(), null, null);
    }

    public void pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }
}
