package com.gazalink.remote;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class RemoteControlService extends AccessibilityService {
    public static RemoteControlService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        MqttBackgroundService.sendToPC("{"type":"STATUS","text":"🟢 خدمة التحكم باللمس مفعلة وجاهزة"}");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        instance = null;
    }

    // تنفيذ نقرة لمس في أي مكان على الشاشة
    public void performTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 80);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }

    // تنفيذ سحب (Swipe) للتمرير
    public void performSwipe(float x1, float y1, float x2, float y2) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 300);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }

    // تنفيذ أزرار النظام
    public void pressHome() { performGlobalAction(GLOBAL_ACTION_HOME); }
    public void pressBack() { performGlobalAction(GLOBAL_ACTION_BACK); }
    public void pressRecents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }

    // فتح تطبيق مباشرة (مثل واتساب)
    public void openApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        }
    }
}
