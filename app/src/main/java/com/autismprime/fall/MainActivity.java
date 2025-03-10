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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    //button objects
    //private Button buttonStart;
    private Button buttonStop,buttonSelectSound;
    private Button buttonOK;
    int acceleration_threshold = 800;
    SensorManager man;
    Sensor sen;
    private EditText editText;
    private TextView speed;
    private SharedPreferences sharedPreferences;

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

        editText=findViewById(R.id.editTextNumberDecimal);
        speed=findViewById(R.id.speed);
        //buttonStart.setOnClickListener(this);
        buttonStop.setOnClickListener(this);
        buttonOK.setOnClickListener(this);
        buttonSelectSound.setOnClickListener(view -> openFileChooser());

        // Load saved preferences
        sharedPreferences = getSharedPreferences("FallDetectionPrefs", MODE_PRIVATE);

        startFallDetectionService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        stopFallDetectionService();
        startFallDetectionService();
    }


    private void startFallDetectionService() {
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

    @Override
    protected void onDestroy(){
        super.onDestroy();
        stopService(new Intent(this, MyService.class));
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
                        sharedPreferences.edit().putString("customSoundUri", uri.toString()).apply();
                        Log.d("CustomSound", "Selected sound URI: " + uri.toString());
                    }
                }
            });

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*"); // Allow only audio files
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }
}