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
    private boolean isAccelerometerDataAvailable = false;
    private boolean isGyroscopeDataAvailable = false;
    private boolean isMagnetometerDataAvailable = false;
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME;
    private static final int SENSOR_TYPE_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private static final int SENSOR_TYPE_GYROSCOPE = Sensor.TYPE_GYROSCOPE;
    private static final int SENSOR_TYPE_MAGNETOMETER = Sensor.TYPE_MAGNETIC_FIELD;

    byte[] accelDataBytes = new byte[3 * 4];
    ByteBuffer accelBuffer = ByteBuffer.wrap(accelDataBytes).order(ByteOrder.LITTLE_ENDIAN);
    byte[] gyroDataBytes = new byte[3 * 4];
    ByteBuffer gyroBuffer = ByteBuffer.wrap(gyroDataBytes).order(ByteOrder.LITTLE_ENDIAN);
    byte[] magnetDataBytes = new byte[3 * 4];
    ByteBuffer magnetBuffer = ByteBuffer.wrap(magnetDataBytes).order(ByteOrder.LITTLE_ENDIAN);

    byte[] allDataBytes = new byte[accelDataBytes.length + gyroDataBytes.length + magnetDataBytes.length];

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
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
                else if (!isMagnetometerDataAvailable && event.sensor.getType() == SENSOR_TYPE_MAGNETOMETER) {
                    magnetBuffer.putFloat(i * 4, event.values[i]);
                    if (i == event.values.length - 1) {
                        isMagnetometerDataAvailable = true;
                    }
                }
            }
            if (isAccelerometerDataAvailable && isGyroscopeDataAvailable && isMagnetometerDataAvailable) {
                // Combine the byte arrays for accelerometer and gyroscope data
                System.arraycopy(accelDataBytes, 0, allDataBytes, 0, accelDataBytes.length);
                System.arraycopy(gyroDataBytes, 0, allDataBytes, accelDataBytes.length, gyroDataBytes.length);
                System.arraycopy(magnetDataBytes, 0, allDataBytes, accelDataBytes.length + gyroDataBytes.length, magnetDataBytes.length);

                // Reset data ready flags
                isAccelerometerDataAvailable = false;
                isGyroscopeDataAvailable = false;
                isMagnetometerDataAvailable = false;

                // Use the same ByteBuffer objects to read the float values
                ByteBuffer buffer = ByteBuffer.wrap(allDataBytes).order(ByteOrder.LITTLE_ENDIAN);

                for (int i = 0; i < 9; i++) {
                    Log.d("Float", String.valueOf(buffer.getFloat()));
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
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                Sensor accelerometerSensor = sensorManager.getDefaultSensor(SENSOR_TYPE_ACCELEROMETER);
                sensorManager.registerListener(sensorListener, accelerometerSensor, SENSOR_DELAY);

                Sensor gyroscopeSensor = sensorManager.getDefaultSensor(SENSOR_TYPE_GYROSCOPE);
                sensorManager.registerListener(sensorListener, gyroscopeSensor, SENSOR_DELAY);

                Sensor magnetometerSensor = sensorManager.getDefaultSensor(SENSOR_TYPE_MAGNETOMETER);
                sensorManager.registerListener(sensorListener, magnetometerSensor, SENSOR_DELAY);

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
