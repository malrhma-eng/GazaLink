package com.gazalink.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import java.util.UUID;

public class MqttBackgroundService extends Service {
    private static MqttClient client;
    private static String topicToPC = "gaza_link_2026/to_pc";
    private String topicToPhone = "gaza_link_2026/to_phone";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "GazaLinkChannel")
                .setContentTitle("GazaLink متصل")
                .setContentText("رابط الطوارئ والتحكم يعمل في الخلفية")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(1, notification);

        SharedPreferences prefs = getSharedPreferences("GazaLinkPrefs", MODE_PRIVATE);
        String room = prefs.getString("room_id", "gaza_link_2026");
        topicToPC = room + "/to_pc";
        topicToPhone = room + "/to_phone";

        new Thread(this::connectMqtt).start();
        return START_STICKY;
    }

    private void connectMqtt() {
        try {
            String clientId = "GazaPhone_" + UUID.randomUUID().toString().substring(0, 8);
            client = new MqttClient("tcp://broker.hivemq.com:1883", clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setKeepAliveInterval(30);

            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    try {
                        client.subscribe(topicToPhone, 1);
                        sendToPC("{"type":"STATUS","text":"📱 الجوال متصل بالشبكة الآن"}");
                    } catch (Exception ignored) {}
                }

                @Override
                public void connectionLost(Throwable cause) {}

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleCommand(new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            client.connect(options);
        } catch (Exception e) {
            try { Thread.sleep(5000); connectMqtt(); } catch (InterruptedException ignored) {}
        }
    }

    private void handleCommand(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            String cmd = json.optString("cmd");
            RemoteControlService ctrl = RemoteControlService.instance;

            switch (cmd) {
                case "TAP":
                    if (ctrl != null) ctrl.performTap((float) json.getDouble("x"), (float) json.getDouble("y"));
                    break;
                case "SWIPE":
                    if (ctrl != null) ctrl.performSwipe(
                        (float) json.getDouble("x1"), (float) json.getDouble("y1"),
                        (float) json.getDouble("x2"), (float) json.getDouble("y2")
                    );
                    break;
                case "HOME":
                    if (ctrl != null) ctrl.pressHome();
                    break;
                case "BACK":
                    if (ctrl != null) ctrl.pressBack();
                    break;
                case "RECENTS":
                    if (ctrl != null) ctrl.pressRecents();
                    break;
                case "OPEN_APP":
                    if (ctrl != null) ctrl.openApp(json.getString("pkg"));
                    break;
                case "ALARM":
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 300, 1000, 300, 1000}, -1));
                        } else {
                            v.vibrate(3000);
                        }
                    }
                    sendToPC("{"type":"STATUS","text":"🔔 تم تشغيل الاهتزاز على الجوال"}");
                    break;
                case "GET_LOC":
                    sendLocation();
                    break;
            }
        } catch (Exception e) {
            sendToPC("{"type":"STATUS","text":"خطأ في تنفيذ الأمر: " + e.getMessage() + ""}");
        }
    }

    private void sendLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                sendToPC("{"type":"LOCATION","lat":" + loc.getLatitude() + ","lon":" + loc.getLongitude() + "}");
            } else {
                sendToPC("{"type":"STATUS","text":"الموقع غير متاح حالياً"}");
            }
        } catch (SecurityException e) {
            sendToPC("{"type":"STATUS","text":"صلاحية الموقع غير ممنوحة"}");
        }
    }

    public static void sendToPC(String jsonMsg) {
        new Thread(() -> {
            try {
                if (client != null && client.isConnected()) {
                    client.publish(topicToPC, new MqttMessage(jsonMsg.getBytes()));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "GazaLinkChannel", "GazaLink Service", NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
