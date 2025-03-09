package com.autismprime.fall;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SensorEventListener{
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        man=(SensorManager)this.getSystemService(Context.SENSOR_SERVICE);
        sen=man.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        man.registerListener(this,sen,SensorManager.SENSOR_DELAY_NORMAL);

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
            speed.setText("needed accelaration to trigger: "+String.valueOf((double)acceleration_threshold/100) +"m/s^2");
            Log.wtf("",editText.getText().toString());
        }
        if (view == buttonStop) {
            if(!stopped) {
                stopped = true;
                buttonStop.setText("RESTART");
            }else{
                stopped=false;
                buttonStop.setText("STOP");
            }
        }
    }
    long lastUpdate=0;
    float lx,ly,lz;

    @Override
    public void onSensorChanged(SensorEvent e){
        if(!stopped&&e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            float x = e.values[0];
            float y = e.values[1];
            float z = e.values[2];

            // Calculate magnitude of acceleration
            double acceleration = Math.sqrt(x * x + y * y + z * z);

            // Check for free fall (threshold ~ 0 to 1 m/s²)
            if (acceleration < 9.81 - (double) acceleration_threshold / 100) {
                startService(new Intent(this, MyService.class));
            }
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){

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