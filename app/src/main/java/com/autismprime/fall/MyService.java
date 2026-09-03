package com.autismprime.fall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
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
    private static final String PREF_TARGET_VOLUME_PERCENT = "targetVolumePercent";
    private static final String PREF_MONITORING_PAUSED = "monitoringPaused";
    private static final String ACTION_TOGGLE_MONITORING = "com.autismprime.fall.ACTION_TOGGLE_MONITORING";
    private static final int NOTIFICATION_ID = 1;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private MediaPlayer player;
    private SharedPreferences sharedPreferences;
    private boolean monitoringPaused = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        sharedPreferences = getSharedPreferences("FallDetectionPrefs", MODE_PRIVATE);
        monitoringPaused = sharedPreferences.getBoolean(PREF_MONITORING_PAUSED, false);

        // Start foreground service with a notification. The notification carries a
        // Stop/Restart action so the user can pause or resume fall detection without
        // having to open the app.
        startForeground(NOTIFICATION_ID, buildNotification(monitoringPaused));

        // Initialize accelerometer for fall detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        if (!monitoringPaused) {
            registerAccelerometerListener();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE_MONITORING.equals(intent.getAction())) {
            toggleMonitoring();
        }
        return START_STICKY; // Keep the service running
    }

    // Called when the user taps the Stop/Restart action on the persistent notification.
    // Unlike the app's own STOP button (which fully stops the service), this just
    // pauses/resumes the accelerometer listener so the foreground notification (and its
    // toggle action) stays available the whole time.
    private void toggleMonitoring() {
        monitoringPaused = !monitoringPaused;
        sharedPreferences.edit().putBoolean(PREF_MONITORING_PAUSED, monitoringPaused).apply();

        if (monitoringPaused) {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            stopPlayer(); // don't let an in-progress alarm keep playing once paused
        } else {
            registerAccelerometerListener();
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(monitoringPaused));
        }
    }

    private void registerAccelerometerListener() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private Notification buildNotification(boolean paused) {
        Intent toggleIntent = new Intent(this, MyService.class).setAction(ACTION_TOGGLE_MONITORING);
        PendingIntent togglePendingIntent = PendingIntent.getService(
                this, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String actionLabel = paused ? "RESTART" : "STOP";
        String contentText = paused ? "Fall detection paused" : "Monitoring for falls...";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fall Detection")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true) // Ensures that the notification is persistent
                .addAction(0, actionLabel, togglePendingIntent)
                .build();
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
            forceConfiguredVolume();

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

    // Forces the device's music-stream volume up to the level the user configured in the
    // app, so the alert is always audible even if the phone's volume was turned down or
    // was affected while it was falling/tumbling. The alert sound is played on
    // STREAM_MUSIC (MediaPlayer's default), so that's the stream we adjust here.
    private void forceConfiguredVolume() {
        int targetVolumePercent = sharedPreferences.getInt(PREF_TARGET_VOLUME_PERCENT, 100);
        // Clamp defensively in case a bad value ever ends up in prefs.
        if (targetVolumePercent < 0) targetVolumePercent = 0;
        if (targetVolumePercent > 100) targetVolumePercent = 100;

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }

        try {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetVolume = Math.round(maxVolume * (targetVolumePercent / 100.0f));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
        } catch (SecurityException e) {
            // Some OEMs restrict volume changes while Do Not Disturb is active; fail
            // quietly rather than crashing the service.
            Log.w("MyService", "Unable to force volume", e);
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