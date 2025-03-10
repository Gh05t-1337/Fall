package com.autismprime.fall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MyService extends Service implements SensorEventListener {
    private static final String CHANNEL_ID = "FallDetectionChannel";
    private static final float FALL_THRESHOLD = 2.0f; // Adjust based on sensitivity
    private SensorManager sensorManager;
    private MediaPlayer player;
    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Start foreground service with notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fall Detection Running")
                .setContentText("Monitoring for falls...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true) // Ensures that the notification is persistent
                .build();
        startForeground(1, notification);

        sharedPreferences = getSharedPreferences("FallDetectionPrefs", MODE_PRIVATE);

        // Initialize accelerometer for fall detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Keep the service running
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int acceleration_threshold = sharedPreferences.getInt("accelerationThreshold", 800); // Default 100 if not set
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Calculate magnitude of acceleration
            double acceleration = Math.sqrt(x * x + y * y + z * z);

            // Check for free fall (threshold ~ 0 to 1 m/s²)
            if (acceleration < 9.81 - (double) acceleration_threshold / 100) {
                triggerFallAlert();
            }
        }
    }

    private void triggerFallAlert() {
        if (player == null) {
            String customSoundUri = sharedPreferences.getString("customSoundUri", null);

            try {
                if (customSoundUri != null) {
                    player = MediaPlayer.create(this, Uri.parse(customSoundUri));
                } else {
                    player = MediaPlayer.create(this, R.raw.song);
                }
            } catch (Exception e) {
                player = null;
            }

            // If custom sound is not chosen or fails, use default
            if (player == null) {
                player = MediaPlayer.create(this, R.raw.song);
            }

            if (player != null) {
                player.setOnCompletionListener(mp -> stopPlayer());
                player.start();
            }
        }
    }

    private void stopPlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        stopPlayer();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for basic fall detection
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fall Detection Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
