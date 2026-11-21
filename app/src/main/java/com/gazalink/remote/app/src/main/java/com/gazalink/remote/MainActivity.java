package com.gazalink.remote;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etBroker, etDeviceId;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etBroker = findViewById(R.id.etBroker);
        etDeviceId = findViewById(R.id.etDeviceId);
        tvStatus = findViewById(R.id.tvStatus);

        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnPanic = findViewById(R.id.btnPanic);

        btnConnect.setOnClickListener(v -> {
            String broker = etBroker.getText().toString().trim();
            String deviceId = etDeviceId.getText().toString().trim();
            if (broker.isEmpty() || deviceId.isEmpty()) {
                Toast.makeText(this, "أدخل عنوان السيرفر ومعرف الجهاز", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent serviceIntent = new Intent(this, MqttBackgroundService.class);
            serviceIntent.putExtra("broker", broker);
            serviceIntent.putExtra("deviceId", deviceId);
            startForegroundService(serviceIntent);
            tvStatus.setText("الحالة: جاري الاتصال بالسيرفر...");
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "فعّل خدمة GazaLink للتحكم عن بعد", Toast.LENGTH_LONG).show();
        });

        btnPanic.setOnClickListener(v -> {
            MqttBackgroundService.sendToPC("{\"type\":\"MSG\",\"text\":\"الطرف ضغط زر: أنا بخير والحمد لله\"}");
            Toast.makeText(this, "تم إرسال رسالة طمأنة للكمبيوتر", Toast.LENGTH_SHORT).show();
        });
    }
}