package com.autismprime.fall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MyService extends Service {
    private static final String CHANNEL_ID = "FallDetectionChannel";
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

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fall Detection Running")
                .setContentText("Monitoring for falls...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification); // Start as Foreground Service

        sharedPreferences = getSharedPreferences("FallDetectionPrefs", MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String customSoundUri = sharedPreferences.getString("customSoundUri", null);

        if (player == null) {
            if (customSoundUri != null) {
                try {
                    player = MediaPlayer.create(this, Uri.parse(customSoundUri)); // Load custom sound
                } catch (Exception e) {
                    player = null; // Reset player if there's an error
                }
            }

            // If custom sound is not chosen or fails, use default
            if (player == null) {
                player = MediaPlayer.create(this, R.raw.song);
            }

            if (player != null) {
                player.setOnCompletionListener(mp -> stopPlayer());
                player.start(); // Play the sound
            }
        }

        return START_STICKY; // Keep service running
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
        if (player != null) {
            player.stop();
        }
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
