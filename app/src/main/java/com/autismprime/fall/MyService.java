package com.autismprime.fall;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.provider.Settings;
import androidx.annotation.Nullable;

import com.autismprime.fall.R;

public class MyService extends Service {
    //creating a mediaplayer object
    private MediaPlayer player;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.song);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopPlayer();
                }
            });
        }


        player.start();

       // player = MediaPlayer.create(this,
         //       Settings.System.DEFAULT_RINGTONE_URI);

        //player.setLooping(true);


       // player.start();

        return START_STICKY;
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
        //stopping the player when service is destroyed
        if(player!=null)
            player.stop();
    }
}
