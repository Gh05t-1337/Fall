package com.autismprime.fall;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    //button objects
    //private Button buttonStart;
    private Button buttonStop,buttonSelectSound;
    private ImageButton buttonResetSound;
    private Button buttonOK;
    int acceleration_threshold = 800;
    SensorManager man;
    Sensor sen;
    private EditText editText;
    private TextView speed;
    private SeekBar volumeSeekBar;
    private TextView volumeValue;
    private CheckBox keepRunningInBackgroundCheckBox;
    private SharedPreferences sharedPreferences;
    private static final String PREF_TARGET_VOLUME_PERCENT = "targetVolumePercent";
    private static final String PREF_KEEP_RUNNING_IN_BACKGROUND = "keepRunningInBackground";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.BODY_SENSORS}, 1);
            }
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.wtf("TAG", "onCreate:" );

        man=(SensorManager)this.getSystemService(Context.SENSOR_SERVICE);
        sen=man.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //buttonStart =  findViewById(R.id.buttonStart);
        buttonStop =  findViewById(R.id.buttonStop);
        buttonOK=findViewById(R.id.okButton);
        buttonSelectSound = findViewById(R.id.selectSoundButton);
        buttonResetSound = findViewById(R.id.resetSoundButton);

        editText=findViewById(R.id.editTextNumberDecimal);
        speed=findViewById(R.id.speed);
        volumeSeekBar=findViewById(R.id.volumeSeekBar);
        volumeValue=findViewById(R.id.volumeValue);
        keepRunningInBackgroundCheckBox=findViewById(R.id.keepRunningInBackgroundCheckBox);
        //buttonStart.setOnClickListener(this);
        buttonStop.setOnClickListener(this);
        buttonOK.setOnClickListener(this);
        buttonSelectSound.setOnClickListener(view -> openFileChooser());
        buttonResetSound.setOnClickListener(view -> resetSoundToDefault());

        // Load saved preferences
        sharedPreferences = getSharedPreferences("FallDetectionPrefs", MODE_PRIVATE);

        // Restore previously saved sensitivity so it survives app restarts
        acceleration_threshold = sharedPreferences.getInt("accelerationThreshold", acceleration_threshold);
        editText.setText(String.valueOf(acceleration_threshold));
        speed.setText("needed accelaration to trigger: " + ((double) acceleration_threshold / 100) + "m/s^2");

        setupVolumeControl();
        setupKeepRunningInBackgroundControl();

        startFallDetectionService();
    }

    // Lets the user configure the volume level that gets forced when a fall is detected
    private void setupVolumeControl() {
        int savedVolumePercent = sharedPreferences.getInt(PREF_TARGET_VOLUME_PERCENT, 100);
        volumeSeekBar.setProgress(savedVolumePercent);
        volumeValue.setText(savedVolumePercent + "%");

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeValue.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // no-op
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sharedPreferences.edit().putInt(PREF_TARGET_VOLUME_PERCENT, seekBar.getProgress()).apply();
            }
        });
    }

    // Lets the user decide whether fall detection should keep running in the background
    // after they close the app, or whether closing the app should stop it (default: keep
    // running, matching the app's whole purpose, but some people find the persistent
    // notification/background monitoring annoying and want to opt out).
    private void setupKeepRunningInBackgroundControl() {
        boolean keepRunning = sharedPreferences.getBoolean(PREF_KEEP_RUNNING_IN_BACKGROUND, true);
        keepRunningInBackgroundCheckBox.setChecked(keepRunning);

        keepRunningInBackgroundCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                sharedPreferences.edit().putBoolean(PREF_KEEP_RUNNING_IN_BACKGROUND, isChecked).apply());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        stopFallDetectionService();
        startFallDetectionService();
    }


    private void startFallDetectionService() {
        // Always start fresh: if the last thing that happened was pausing monitoring
        // from the notification's toggle action, a full (re)start from the app should
        // still actually start monitoring, not silently resume in a paused state.
        sharedPreferences.edit().putBoolean("monitoringPaused", false).apply();
        Intent serviceIntent = new Intent(this, MyService.class);
        startService(serviceIntent);
        //statusText.setText("Fall Detection Running...");
        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
    }

    private void stopFallDetectionService() {
        Intent serviceIntent = new Intent(this, MyService.class);
        stopService(serviceIntent);
        //statusText.setText("Fall Detection Stopped.");
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show();
    }

    // Whether MyService keeps running here depends on the "keep monitoring in background"
    // checkbox the user set on screen (persisted in PREF_KEEP_RUNNING_IN_BACKGROUND):
    //  - checked (default): the foreground service is left alone, so fall detection keeps
    //    monitoring after the user closes/leaves this Activity.
    //  - unchecked: closing the app also stops the service, since the user explicitly
    //    said they don't want background monitoring.
    // Previously this method unconditionally force-stopped the service on every
    // onDestroy(), which meant fall detection silently stopped as soon as the app was
    // closed, with no way to opt into background monitoring at all.
    @Override
    protected void onDestroy(){
        super.onDestroy();
        boolean keepRunning = sharedPreferences.getBoolean(PREF_KEEP_RUNNING_IN_BACKGROUND, true);
        if (!keepRunning) {
            stopService(new Intent(this, MyService.class));
        }
    }

    boolean stopped=false;
    @Override
    public void onClick(View view) {
        if(view==buttonOK){
            acceleration_threshold = Integer.parseInt(editText.getText().toString());
            sharedPreferences.edit().putInt("accelerationThreshold", acceleration_threshold).apply();
            speed.setText("needed accelaration to trigger: "+String.valueOf((double)acceleration_threshold/100) +"m/s^2");
            Log.wtf("",editText.getText().toString());
        }
        if (view == buttonStop) {
            if(!stopped) {
                stopped = true;
                stopFallDetectionService();
                buttonStop.setText("RESTART");
            }else{
                stopped=false;
                startFallDetectionService();
                buttonStop.setText("STOP");
            }
        }
    }

    // File chooser to select custom sound
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // ACTION_OPEN_DOCUMENT only grants temporary read access tied to
                        // this Activity instance. Since the fall-detection service is meant
                        // to keep running (and play this sound) long after MainActivity is
                        // destroyed, we need a *persistable* grant, or the permission gets
                        // revoked as soon as the app is closed and the custom sound silently
                        // stops working (falling back to the default sound).
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            Log.w("CustomSound", "Could not persist read permission for " + uri, e);
                        }
                        sharedPreferences.edit().putString("customSoundUri", uri.toString()).apply();
                        Log.d("CustomSound", "Selected sound URI: " + uri.toString());
                        Toast.makeText(this, "Custom sound saved", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*"); // Allow only audio files
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Request a persistable grant up front so takePersistableUriPermission() succeeds.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        filePickerLauncher.launch(intent);
    }

    // Clears the chosen custom sound so the app falls back to the bundled default alert
    // sound (R.raw.song) again. Also releases the persisted read permission we took on
    // that URI in the file picker callback, since we no longer need access to it.
    private void resetSoundToDefault() {
        String customSoundUri = sharedPreferences.getString("customSoundUri", null);
        if (customSoundUri != null) {
            try {
                getContentResolver().releasePersistableUriPermission(
                        Uri.parse(customSoundUri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                // We may not hold this permission anymore (e.g. it was already revoked
                // by the system or the provider); nothing to clean up in that case.
                Log.w("CustomSound", "Could not release permission for " + customSoundUri, e);
            }
        }
        sharedPreferences.edit().remove("customSoundUri").apply();
        Toast.makeText(this, "Reset to default sound", Toast.LENGTH_SHORT).show();
    }
}