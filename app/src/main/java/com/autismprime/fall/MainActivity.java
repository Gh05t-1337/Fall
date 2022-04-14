package com.autismprime.fall;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.autismprime.fall.R;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SensorEventListener{
    //button objects
    //private Button buttonStart;
    private Button buttonStop;
    private Button buttonOK;
    int SHAKE_THRESHOLD=800;
    SensorManager man;
    Sensor sen;
    private EditText editText;
    private TextView speed;

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

        editText=findViewById(R.id.editTextNumberDecimal);
        speed=findViewById(R.id.speed);
        //buttonStart.setOnClickListener(this);
        buttonStop.setOnClickListener(this);
        buttonOK.setOnClickListener(this);
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
            SHAKE_THRESHOLD=Integer.parseInt(editText.getText().toString());
            speed.setText("needed speed to trigger: "+editText.getText().toString());
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
            long curTim=System.currentTimeMillis();

            if(curTim-lastUpdate>100){
                long diff=curTim-lastUpdate;
                lastUpdate=curTim;

                float speed=Math.abs(e.values[0]+e.values[1]+e.values[2]-lx-ly-lz)/diff*10000;

                if(speed>SHAKE_THRESHOLD){
                    startService(new Intent(this, MyService.class));
                }

                lx=e.values[0];
                ly=e.values[1];
                lz=e.values[2];
            }
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){

    }
}
