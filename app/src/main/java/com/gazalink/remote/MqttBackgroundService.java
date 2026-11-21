package com.gazalink.remote;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public class MqttBackgroundService extends Service {
    private static MqttClient client;
    private static String topicToPc;
    private String topicToPhone;
    private static final String BROKER = "tcp://broker.hivemq.com:1883";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String room = intent != null ? intent.getStringExtra("room") : "gaza-family-2026";
        if (room == null) room = "gaza-family-2026";

        topicToPc = "gazalink/" + room + "/to_pc";
        topicToPhone = "gazalink/" + room + "/to_phone";

        startForegroundNotification();
        connectMqtt();

        return START_STICKY;
    }

    private void connectMqtt() {
        new Thread(() -> {
            try {
                if (client != null && client.isConnected()) {
                    client.disconnect();
                }
                client = new MqttClient(BROKER, MqttClient.generateClientId(), new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);

                client.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {}

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                        handleCommand(payload);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {}
                });

                client.connect(options);
                client.subscribe(topicToPhone, 1);
                sendToPC("{\"type\":\"STATUS\",\"text\":\"📱 الجوال متصل بالشبكة الآن\"}");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleCommand(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            String cmd = obj.optString("cmd", "");

            switch (cmd) {
                case "CLICK":
                    int x = obj.optInt("x", 500);
                    int y = obj.optInt("y", 500);
                    if (RemoteControlService.instance != null) {
                        RemoteControlService.instance.performTap(x, y);
                    }
                    break;

                case "SWIPE":
                    int x1 = obj.optInt("x1", 500);
                    int y1 = obj.optInt("y1", 1200);
                    int x2 = obj.optInt("x2", 500);
                    int y2 = obj.optInt("y2", 400);
                    if (RemoteControlService.instance != null) {
                        RemoteControlService.instance.performSwipe(x1, y1, x2, y2);
                    }
                    break;

                case "BACK":
                    if (RemoteControlService.instance != null) {
                        RemoteControlService.instance.pressBack();
                    }
                    break;

                case "HOME":
                    if (RemoteControlService.instance != null) {
                        RemoteControlService.instance.pressHome();
                    }
                    break;

                case "VIBRATE":
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            v.vibrate(1000);
                        }
                    }
                    sendToPC("{\"type\":\"STATUS\",\"text\":\"🔔 تم تشغيل الاهتزاز على الجوال\"}");
                    break;

                case "LOCATION":
                    sendCurrentLocation();
                    break;
            }
        } catch (Exception e) {
            sendToPC("{\"type\":\"STATUS\",\"text\":\"خطأ في تنفيذ الأمر: " + e.getMessage() + "\"}");
        }
    }

    private void sendCurrentLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc != null) {
                sendToPC("{\"type\":\"LOCATION\",\"lat\":" + loc.getLatitude() + ",\"lon\":" + loc.getLongitude() + "}");
            } else {
                sendToPC("{\"type\":\"STATUS\",\"text\":\"الموقع غير متاح حالياً\"}");
            }
        } else {
            sendToPC("{\"type\":\"STATUS\",\"text\":\"صلاحية الموقع غير ممنوحة\"}");
        }
    }

    public static void sendToPC(String text) {
        new Thread(() -> {
            try {
                if (client != null && client.isConnected() && topicToPc != null) {
                    client.publish(topicToPc, new MqttMessage(text.getBytes(StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startForegroundNotification() {
        String channelId = "GazaLinkChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "GazaLink Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("GazaLink متصل")
                .setContentText("قناة الدعم والمساعدة عن بعد نشطة")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(1, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
