package com.myapp.sensorsender;

import com.kircherelectronics.fsensor.observer.SensorSubject;
import com.kircherelectronics.fsensor.sensor.FSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.KalmanLinearAccelerationSensor;
import com.kircherelectronics.fsensor.sensor.gyroscope.ComplementaryGyroscopeSensor;
import com.kircherelectronics.fsensor.sensor.gyroscope.KalmanGyroscopeSensor;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.Locale;

import io.reactivex.disposables.CompositeDisposable;


public class MainActivity extends AppCompatActivity {
    public Button connectButton;
    public Button disconnectButton;
    public Button startSendingButton;
    public Button stopSendingButton;
    public FSensor accelerometerSensor;
    public FSensor gyroSensor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectButton = findViewById(R.id.connect_button);
        disconnectButton = findViewById(R.id.disconnect_button);
        startSendingButton = findViewById(R.id.start_sending_button);
        stopSendingButton = findViewById(R.id.stop_sending_button);

        FSensorTest fSensorTest = new FSensorTest(MainActivity.this);

        SensorListener sensorListener = new SensorListener(MainActivity.this);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sensorListener.connectToServer();
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sensorListener.disconnectFromServer();
            }
        });

        startSendingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sensorListener.startSendingSensorData();
            }
        });

        stopSendingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sensorListener.stopSendingSensorData();
            }
        });
    }

    private final SensorSubject.SensorObserver accelerometerObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            String linearAccelerationString =
                    String.format(Locale.US, "%.2f", values[0]) + ", " +
                    String.format(Locale.US, "%.2f", values[1]) + ", " +
                    String.format(Locale.US, "%.2f", values[2]);

            Log.d("Accelerometer", linearAccelerationString);
        }
    };

    private final SensorSubject.SensorObserver gyroObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            String gyroString =
                    String.format(Locale.US, "%.2f", values[0]) + ", " +
                    String.format(Locale.US, "%.2f", values[1]) + ", " +
                    String.format(Locale.US, "%.2f", values[2]);

            Log.d("Gyro", gyroString);
        }
    };


    @Override
    public void onResume() {
        super.onResume();
        accelerometerSensor = new KalmanLinearAccelerationSensor(this);
        accelerometerSensor.register(accelerometerObserver);
        accelerometerSensor.start();

        gyroSensor = new ComplementaryGyroscopeSensor(this);
        gyroSensor.register(gyroObserver);
        gyroSensor.start();
    }

    @Override
    public void onPause() {
        accelerometerSensor.unregister(accelerometerObserver);
        accelerometerSensor.stop();

        gyroSensor.unregister(gyroObserver);
        gyroSensor.stop();

        super.onPause();
    }

}

