package com.myapp.sensorsender;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SensorListener implements SensorEventListener {
    private MainActivity mainActivity;

    public SensorListener(MainActivity activity) {
        this.mainActivity = activity;
    }

    private Socket socket;
    private OutputStream outputStream;
    private boolean isSendingData = false;
    private SensorManager sensorManager;
    private SensorEventListener sensorListener = this;
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL;
    private static final int SENSOR_TYPE_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private static final int SENSOR_TYPE_GYROSCOPE = Sensor.TYPE_GYROSCOPE;
    private static final int SENSOR_TYPE_MAGNETOMETER = Sensor.TYPE_MAGNETIC_FIELD;
    private static final int SENSOR_TYPE_GAME_ROTATION_VECTOR = Sensor.TYPE_GAME_ROTATION_VECTOR;




    private long lastPacketTime = 0;
    private final long delayTime = 100; // in milliseconds
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Send the sensor data to the server
        try {
            /*
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
                else if (!isMagnetometerDataAvailable && event.sensor.getType() == SENSOR_TYPE_MAGNETOMETER) {
                    magnetBuffer.putFloat(i * 4, event.values[i]);
                    if (i == event.values.length - 1) {
                        isMagnetometerDataAvailable = true;
                    }
                }
            }
            */

            long currentTime = System.currentTimeMillis();

            /*
            // Add a delay if the time difference between the current time and the last packet time is less than the desired delay time
            if (currentTime - lastPacketTime < delayTime) {
                try {
                    Thread.sleep(delayTime - (currentTime - lastPacketTime));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            */
            
            byte[] rotationQuaternionBytes = new byte[4 * 4];
            ByteBuffer rotationQuaternionBytesBuffer = ByteBuffer.wrap(rotationQuaternionBytes).order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < event.values.length; i++) {
                if(event.sensor.getType() == SENSOR_TYPE_GAME_ROTATION_VECTOR){
                    rotationQuaternionBytesBuffer.putFloat(i * 4, event.values[i]);
                }
            }

            if(event.values.length == 3){
                rotationQuaternionBytesBuffer.putFloat(12, 0);
            }

            for (int i = 0; i < 4; i++) {
                Log.d("Float", String.valueOf(rotationQuaternionBytesBuffer.getFloat()));
            }

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
                        rotationQuaternionBytesBuffer.rewind();
                        rotationQuaternionBytesBuffer.get(rotationQuaternionBytes);
                        dataOutputStream.write(rotationQuaternionBytes);
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
        lastPacketTime = System.currentTimeMillis();
    }

    public void connectToServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress("10.0.0.157", 1234), 5000);
                    outputStream = socket.getOutputStream();
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mainActivity, "Connected to server", Toast.LENGTH_SHORT).show();

                            // Enable all buttons except the "Connect" button
                            mainActivity.connectButton.setEnabled(false);
                            mainActivity.disconnectButton.setEnabled(true);
                            mainActivity.startSendingButton.setEnabled(true);
                            mainActivity.stopSendingButton.setEnabled(false);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mainActivity, "Could not connect to server", Toast.LENGTH_SHORT).show();
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
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mainActivity, "Disconnected from server", Toast.LENGTH_SHORT).show();

                            // Disable all buttons except the "Connect" button
                            mainActivity.connectButton.setEnabled(true);
                            mainActivity.disconnectButton.setEnabled(false);
                            mainActivity.startSendingButton.setEnabled(false);
                            mainActivity.stopSendingButton.setEnabled(false);
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

        // Create a new thread to run the sensor data sending code
        Thread sensorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // Create the sensor manager and sensor listener
                sensorManager = (SensorManager) mainActivity.getSystemService(Context.SENSOR_SERVICE);

                // Register the sensor listener for accelerometer and gyroscope sensors
                Sensor gameRotationVectorSensor = sensorManager.getDefaultSensor(SENSOR_TYPE_GAME_ROTATION_VECTOR);
                sensorManager.registerListener(sensorListener, gameRotationVectorSensor, SENSOR_DELAY);

                // Set the isSendingData flag to true
                isSendingData = true;
            }
        });

        // Start the thread
        sensorThread.start();

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mainActivity, "Started sending data", Toast.LENGTH_SHORT).show();

                // Enable all buttons except the "Connect" button
                mainActivity.connectButton.setEnabled(false);
                mainActivity.disconnectButton.setEnabled(true);
                mainActivity.startSendingButton.setEnabled(false);
                mainActivity.stopSendingButton.setEnabled(true);
            }
        });
    }

    public void stopSendingSensorData() {
        // Check if we're currently sending data
        if (!isSendingData) {
            return;
        }

        // Unregister the sensor listener
        sensorManager.unregisterListener(sensorListener);

        // Set the flag indicating that we're not sending data
        isSendingData = false;
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mainActivity, "Stopped sending data", Toast.LENGTH_SHORT).show();

                // Enable all buttons except the "Connect" button
                mainActivity.connectButton.setEnabled(false);
                mainActivity.disconnectButton.setEnabled(true);
                mainActivity.startSendingButton.setEnabled(true);
                mainActivity.stopSendingButton.setEnabled(false);
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }
}
