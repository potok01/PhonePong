package com.myapp.sensorsender;

import com.kircherelectronics.fsensor.observer.SensorSubject;
import com.kircherelectronics.fsensor.sensor.FSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.KalmanLinearAccelerationSensor;
import com.kircherelectronics.fsensor.sensor.gyroscope.KalmanGyroscopeSensor;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    public Button connectButton;
    public Button disconnectButton;
    public Button startSendingButton;
    public Button stopSendingButton;
    public FSensor accelerometerSensor;
    public FSensor gyroSensor;
    private Socket socket;
    private OutputStream outputStream;
    private boolean isSendingData = false;
    private boolean shouldStateChangeSensors = false;
    float azimuth;
    float pitch;
    float roll;
    float[] q_azimuth;
    float[] q_pitch;
    float[] q_roll;
    float[] q;
    boolean isLinearAccelerationDataReady;
    boolean isGyroDataReady;

    byte[] allDataBytes = new byte[9 * 4];
    ByteBuffer allDataBytesBuffer = ByteBuffer.wrap(allDataBytes).order(ByteOrder.LITTLE_ENDIAN);
    byte[] linearAccelerationDataBytes = new byte[4 * 4];
    ByteBuffer linearAccelerationDataBytesBuffer = ByteBuffer.wrap(linearAccelerationDataBytes).order(ByteOrder.LITTLE_ENDIAN);
    byte[] gyroDataBytes = new byte[5 * 4];
    ByteBuffer gyroDataBytesBuffer = ByteBuffer.wrap(gyroDataBytes).order(ByteOrder.LITTLE_ENDIAN);



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

    private final SensorSubject.SensorObserver accelerometerObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            if(isLinearAccelerationDataReady)
                return;

            for (int i = 0; i < values.length; i++) {
                linearAccelerationDataBytesBuffer.putFloat(i * 4, values[i]);
            }

            String linearAccelerationString = "Linear Acceleration: " +
                    String.format(Locale.US, "%.2f", values[0]) + ", " +
                    String.format(Locale.US, "%.2f", values[1]) + ", " +
                    String.format(Locale.US, "%.2f", values[2]) + ", " +
                    String.format(Locale.US, "%.2f", values[3]);

            Log.d("Accelerometer", linearAccelerationString);

            isLinearAccelerationDataReady = true;

            if (isGyroDataReady) {
                sendDataToServer();
            }
        }
    };
    private final SensorSubject.SensorObserver gyroObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            if(isGyroDataReady)
                return;

            azimuth = values[0];
            pitch = values[1];
            roll = values[2];

            q_azimuth = new float[]{(float)Math.cos(azimuth/2f), 0f, 0f, (float)Math.sin(azimuth/2f)};
            q_pitch = new float[]{(float)Math.cos(pitch/2f), 0f, (float)Math.sin(pitch/2f), 0f};
            q_roll = new float[]{(float)Math.cos(roll/2f), (float)Math.sin(roll/2f), 0f, 0f};


            q = new float[5];
            q[0] = q_azimuth[0] * q_pitch[0] * q_roll[0] - q_azimuth[1] * q_pitch[1] * q_roll[0] - q_azimuth[2] * q_pitch[0] * q_roll[1] - q_azimuth[3] * q_pitch[1] * q_roll[1];
            q[1] = q_azimuth[0] * q_pitch[1] * q_roll[0] + q_azimuth[1] * q_pitch[0] * q_roll[0] + q_azimuth[2] * q_pitch[1] * q_roll[1] - q_azimuth[3] * q_pitch[0] * q_roll[1];
            q[2] = q_azimuth[0] * q_pitch[0] * q_roll[1] + q_azimuth[1] * q_pitch[1] * q_roll[1] + q_azimuth[2] * q_pitch[0] * q_roll[0] - q_azimuth[3] * q_pitch[1] * q_roll[0];
            q[3] = q_azimuth[0] * q_pitch[1] * q_roll[1] - q_azimuth[1] * q_pitch[0] * q_roll[1] + q_azimuth[2] * q_pitch[1] * q_roll[0] + q_azimuth[3] * q_pitch[0] * q_roll[0];
            q[4] = values[3];

            for (int i = 0; i < q.length; i++) {
                gyroDataBytesBuffer.putFloat(i * 4, q[i]);
            }

            String gyroString = "Gyroscope: " +
                    String.format(Locale.US, "%.2f", q[0]) + ", " +
                    String.format(Locale.US, "%.2f", q[1]) + ", " +
                    String.format(Locale.US, "%.2f", q[2]) + ", " +
                    String.format(Locale.US, "%.2f", q[3]) + ", " +
                    String.format(Locale.US, "%.2f", q[4]);

            Log.d("Gyro", gyroString);

            isGyroDataReady = true;

            if (isLinearAccelerationDataReady) {
                sendDataToServer();
            }
        }
    };

    public void startSensors(){
        accelerometerSensor = new KalmanLinearAccelerationSensor(this);
        accelerometerSensor.register(accelerometerObserver);
        accelerometerSensor.start();

        gyroSensor = new KalmanGyroscopeSensor(this);
        gyroSensor.register(gyroObserver);
        gyroSensor.start();
    }

    public void stopSensors(){
        accelerometerSensor.unregister(accelerometerObserver);
        accelerometerSensor.stop();

        gyroSensor.unregister(gyroObserver);
        gyroSensor.stop();
    }
    @Override
    public void onResume() {
        super.onResume();
        if(shouldStateChangeSensors)
            startSensors();
    }

    @Override
    public void onPause() {
        if(shouldStateChangeSensors)
            stopSensors();

        super.onPause();
    }

    public void connectToServer() {
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
                            connectButton.setEnabled(false);
                            disconnectButton.setEnabled(true);
                            startSendingButton.setEnabled(true);
                            stopSendingButton.setEnabled(false);
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

    public void disconnectFromServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.close();
                    stopSendingSensorData();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Disconnected from server", Toast.LENGTH_SHORT).show();

                            // Disable all buttons except the "Connect" button
                            connectButton.setEnabled(true);
                            disconnectButton.setEnabled(false);
                            startSendingButton.setEnabled(false);
                            stopSendingButton.setEnabled(false);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void startSendingSensorData() {
        // Check if we're already sending data
        if (isSendingData) {
            return;
        }

        isSendingData = true;
        startSensors();

        shouldStateChangeSensors = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Started sending data", Toast.LENGTH_SHORT).show();

                // Enable all buttons except the "Connect" button
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                startSendingButton.setEnabled(false);
                stopSendingButton.setEnabled(true);
            }
        });
    }

    public void stopSendingSensorData() {
        // Check if we're currently sending data
        if (!isSendingData) {
            return;
        }

        stopSensors();
        shouldStateChangeSensors = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Stopped sending data", Toast.LENGTH_SHORT).show();

                // Enable all buttons except the "Connect" button
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                startSendingButton.setEnabled(true);
                stopSendingButton.setEnabled(false);
            }
        });
    }

    public void sendDataToServer() {
        isGyroDataReady = false;
        isLinearAccelerationDataReady = false;

        System.arraycopy(linearAccelerationDataBytes, 0, allDataBytes, 0, linearAccelerationDataBytes.length);
        System.arraycopy(gyroDataBytes, 0, allDataBytes, linearAccelerationDataBytes.length, gyroDataBytes.length);

        // Send the sensor data to the server
        try {
            // Send the data to the server on a separate thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Check if the socket is still connected
                        if (!socket.isConnected()) {
                            Log.e("Socket", "Connection closed");
                            return;
                        }
                        // Write the data to the output stream
                        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                        allDataBytesBuffer.rewind();
                        allDataBytesBuffer.get(allDataBytes);
                        dataOutputStream.write(allDataBytes);
                        dataOutputStream.flush();
                    } catch (SocketException e) {
                        // Handle the broken pipe error
                        Log.e("Socket", "Remote server closed");
                        disconnectFromServer();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
            ).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

