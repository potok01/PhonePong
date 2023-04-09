package com.myapp.sensorsender;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kircherelectronics.fsensor.observer.SensorSubject;
import com.kircherelectronics.fsensor.sensor.FSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.KalmanLinearAccelerationSensor;

public class FSensorTest extends AppCompatActivity {
    private MainActivity mainActivity;
    public FSensorTest() {
    }
    public FSensorTest(MainActivity activity){
    }
    private FSensor fSensor;
    private SensorSubject.SensorObserver sensorObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            String linearAccelerationString = "Linear Acceleration: " +
                    String.format("%.2f", values[0]) + ", " +
                    String.format("%.2f", values[1]) + ", " +
                    String.format("%.2f", values[2]);

            Log.d("Values", linearAccelerationString);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        fSensor = new KalmanLinearAccelerationSensor(this);
        fSensor.register(sensorObserver);
        fSensor.start();
    }

    @Override
    public void onPause() {
        fSensor.unregister(sensorObserver);
        fSensor.stop();

        super.onPause();
    }
}
