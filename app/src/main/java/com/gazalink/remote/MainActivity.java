package com.gazalink.remote;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText roomInput = findViewById(R.id.roomInput);
        TextView statusText = findViewById(R.id.statusText);
        Button btnEnableAccess = findViewById(R.id.btnEnableAccess);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnImOk = findViewById(R.id.btnImOk);

        SharedPreferences prefs = getSharedPreferences("GazaLinkPrefs", MODE_PRIVATE);
        roomInput.setText(prefs.getString("room", "gaza-family-2026"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 1);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 1);
        }

        btnEnableAccess.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "ابحث عن GazaLink وقم بتفعيله", Toast.LENGTH_LONG).show();
        });

        btnStart.setOnClickListener(v -> {
            String room = roomInput.getText().toString().trim();
            if (room.isEmpty()) return;
            prefs.edit().putString("room", room).apply();

            Intent serviceIntent = new Intent(this, MqttBackgroundService.class);
            serviceIntent.putExtra("room", room);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            statusText.setText("الحالة: 🟢 الخدمة تعمل في الخلفية");
        });

        btnImOk.setOnClickListener(v -> {
            MqttBackgroundService.sendToPC("{\"type\":\"MSG\",\"text\":\"✅ الوالد ضغط زر: أنا بخير والحمد لله\"}");
            Toast.makeText(this, "تم إرسال رسالة الطمأنة", Toast.LENGTH_SHORT).show();
        });
    }
}
