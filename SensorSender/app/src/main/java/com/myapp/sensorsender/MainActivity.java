package com.myapp.sensorsender;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    private class MySensorEventListener implements SensorEventListener {
        byte[] accelDataBytes = new byte[3 * 4];
        ByteBuffer accelBuffer = ByteBuffer.wrap(accelDataBytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] gyroDataBytes = new byte[3 * 4];
        ByteBuffer gyroBuffer = ByteBuffer.wrap(gyroDataBytes).order(ByteOrder.LITTLE_ENDIAN);
        @Override
        public void onSensorChanged(SensorEvent event) {
            // Send the sensor data to the server
            try {
                // Convert the sensor data to byte arrays
                for (int i = 0; i < event.values.length; i++) {
                    if (!isAccelerometerDataAvailable && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        accelBuffer.putFloat(i * 4, event.values[i]);
                        if (i == event.values.length - 1) {
                            isAccelerometerDataAvailable = true;
                        }
                    } else if (!isGyroscopeDataAvailable && event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        gyroBuffer.putFloat(i * 4, event.values[i]);
                        if (i == event.values.length - 1) {
                            isGyroscopeDataAvailable = true;
                        }
                    }
                }
                if (isAccelerometerDataAvailable && isGyroscopeDataAvailable) {
                    // Combine the byte arrays for accelerometer and gyroscope data
                    byte[] allDataBytes = new byte[accelDataBytes.length + gyroDataBytes.length];
                    System.arraycopy(accelDataBytes, 0, allDataBytes, 0, accelDataBytes.length);
                    System.arraycopy(gyroDataBytes, 0, allDataBytes, accelDataBytes.length, gyroDataBytes.length);

                    isAccelerometerDataAvailable = false;
                    isGyroscopeDataAvailable = false;

                    // Use the same ByteBuffer objects to read the float values
                    ByteBuffer buffer = ByteBuffer.wrap(allDataBytes).order(ByteOrder.LITTLE_ENDIAN);

                    for (int i = 0; i < 6; i++) {
                        Log.d("Float", String.valueOf(buffer.getFloat()));
                    }

                    // Send the data to the server on a separate thread
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Write the data to the output stream
                                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                                dataOutputStream.write(allDataBytes);
                                dataOutputStream.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do nothing
        }
    }

    private Socket socket;
    private OutputStream outputStream;
    private Button connectButton;
    private Button disconnectButton;
    private Button startSendingButton;
    private Button stopSendingButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectButton = findViewById(R.id.connect_button);
        disconnectButton = findViewById(R.id.disconnect_button);
        startSendingButton = findViewById(R.id.start_sending_button);
        stopSendingButton = findViewById(R.id.stop_sending_button);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToServer();
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectFromServer();
            }
        });

        startSendingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSendingSensorData();
            }
        });

        stopSendingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSendingSensorData();
            }
        });
    }

    private void connectToServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress("10.0.0.157", 1234), 5000);
                    outputStream = socket.getOutputStream();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show();

                            // Enable all buttons except the "Connect" button
                            disconnectButton.setEnabled(true);
                            startSendingButton.setEnabled(true);
                            stopSendingButton.setEnabled(true);
                            connectButton.setEnabled(false);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Could not connect to server", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();

    }

    private void disconnectFromServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Disconnected from server", Toast.LENGTH_SHORT).show();

                            // Disable all buttons except the "Connect" button
                            disconnectButton.setEnabled(false);
                            startSendingButton.setEnabled(false);
                            stopSendingButton.setEnabled(false);
                            connectButton.setEnabled(true);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean isSendingData = false;
    private SensorManager sensorManager;
    private SensorEventListener sensorListener;

    private boolean isAccelerometerDataAvailable = false;
    private boolean isGyroscopeDataAvailable = false;

    private StringBuilder allData = new StringBuilder();
    private StringBuilder accelData = new StringBuilder();
    private StringBuilder gyroData = new StringBuilder();

    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME;
    private static final int SENSOR_TYPE_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private static final int SENSOR_TYPE_GYROSCOPE = Sensor.TYPE_GYROSCOPE;

    private void startSendingSensorData() {
        // Check if we're already sending data
        if (isSendingData) {
            return;
        }

        // Create a new thread to run the sensor data sending code
        Thread sensorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // Create the sensor manager and sensor listener
                sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                sensorListener = new MySensorEventListener();

                // Register the sensor listener for accelerometer and gyroscope sensors
                Sensor accelerometerSensor = sensorManager.getDefaultSensor(SENSOR_TYPE_ACCELEROMETER);
                sensorManager.registerListener(sensorListener, accelerometerSensor, SENSOR_DELAY);

                Sensor gyroscopeSensor = sensorManager.getDefaultSensor(SENSOR_TYPE_GYROSCOPE);
                sensorManager.registerListener(sensorListener, gyroscopeSensor, SENSOR_DELAY);

                // Set the isSendingData flag to true
                isSendingData = true;
            }
        });

        // Start the thread
        sensorThread.start();
    }




    private void stopSendingSensorData() {
        // Check if we're currently sending data
        if (!isSendingData) {
            return;
        }

        // Unregister the sensor listener
        sensorManager.unregisterListener(sensorListener);

        // Set the flag indicating that we're not sending data
        isSendingData = false;
    }


}