package com.myapp.sensorsender;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;


public class MainActivity extends AppCompatActivity {

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
                sensorListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        // Send the sensor data as a string to the server
                        try {
                            // Convert the sensor data to a string
                            StringBuilder stringBuilder = new StringBuilder();
                            for (int i = 0; i < event.values.length; i++) {
                                stringBuilder.append(event.values[i]);
                                if (i != event.values.length - 1) {
                                    stringBuilder.append(",");
                                }
                            }
                            String data = stringBuilder.toString();

                            // Send the data to the server on a separate thread
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    PrintWriter printWriter = new PrintWriter(outputStream, true);
                                    printWriter.println(data);
                                }
                            }).start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {
                        // Do nothing
                    }
                };

                // Register the sensor listener
                sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

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