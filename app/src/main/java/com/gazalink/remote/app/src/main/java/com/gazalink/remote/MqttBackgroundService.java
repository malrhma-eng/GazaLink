package com.gazalink.remote;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

public class MqttBackgroundService extends Service {

    private static MqttClient client;
    private static String topicCommand = "gazalink/device1/cmd";
    private static String topicStatus = "gazalink/device1/status";
    private static final String CHANNEL_ID = "GazaLinkChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GazaLink متصل")
                .setContentText("خدمة التحكم عن بعد تعمل في الخلفية")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String broker = intent.getStringExtra("broker");
            String deviceId = intent.getStringExtra("deviceId");
            if (deviceId != null && !deviceId.isEmpty()) {
                topicCommand = "gazalink/" + deviceId + "/cmd";
                topicStatus = "gazalink/" + deviceId + "/status";
            }
            if (broker != null && !broker.isEmpty()) {
                connectMqtt(broker, deviceId);
            }
        }
        return START_STICKY;
    }

    private void connectMqtt(String brokerUrl, String deviceId) {
        new Thread(() -> {
            try {
                client = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);

                client.setCallback(new MqttCallback() {
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
                client.subscribe(topicCommand);
                sendToPC("{\"type\":\"STATUS\",\"text\":\"الجوال متصل بالشبكة الآن\"}");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleCommand(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            String action = json.optString("action");

            if ("TAP".equals(action)) {
                float x = (float) json.optDouble("x", 500);
                float y = (float) json.optDouble("y", 500);
                if (RemoteControlService.instance != null) {
                    RemoteControlService.instance.performTap(x, y);
                }
            } else if ("SWIPE".equals(action)) {
                float x1 = (float) json.optDouble("x1", 500);
                float y1 = (float) json.optDouble("y1", 1000);
                float x2 = (float) json.optDouble("x2", 500);
                float y2 = (float) json.optDouble("y2", 300);
                if (RemoteControlService.instance != null) {
                    RemoteControlService.instance.performSwipe(x1, y1, x2, y2, 300);
                }
            } else if ("GLOBAL".equals(action)) {
                int code = json.optInt("code", 1);
                if (RemoteControlService.instance != null) {
                    RemoteControlService.instance.performGlobal(code);
                }
            } else if ("UNLOCK".equals(action)) {
                if (RemoteControlService.instance != null) {
                    RemoteControlService.instance.performSwipe(500, 1600, 500, 400, 250);
                    sendToPC("{\"type\":\"STATUS\",\"text\":\"تم تشغيل الاهتزاز على الجوال\"}");
                }
            } else if ("LOCATION".equals(action)) {
                sendLocation();
            } else if ("PING".equals(action)) {
                sendToPC("{\"type\":\"STATUS\",\"text\":\"الجوال متصل ويستجيب بنجاح\"}");
            }
        } catch (Exception e) {
            sendToPC("{\"type\":\"STATUS\",\"text\":\"خطأ في تنفيذ الأمر: " + e.getMessage() + "\"}");
        }
    }

    private void sendLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                sendToPC("{\"type\":\"STATUS\",\"text\":\"صلاحية الموقع غير ممنوحة\"}");
                return;
            }
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (loc != null) {
                sendToPC("{\"type\":\"LOCATION\",\"lat\":" + loc.getLatitude() + ",\"lon\":" + loc.getLongitude() + "}");
            } else {
                sendToPC("{\"type\":\"STATUS\",\"text\":\"الموقع غير متاح حالياً\"}");
            }
        } catch (Exception e) {
            sendToPC("{\"type\":\"STATUS\",\"text\":\"تعذر جلب الموقع\"}");
        }
    }

    public static void sendToPC(String msg) {
        new Thread(() -> {
            try {
                if (client != null && client.isConnected()) {
                    client.publish(topicStatus, new MqttMessage(msg.getBytes()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "GazaLink Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}