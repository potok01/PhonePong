using System;
using System.Collections;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using TMPro;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.UIElements;
using Button = UnityEngine.UI.Button;
using Debug = UnityEngine.Debug;

public class SensorServer : MonoBehaviour
{
    public int port = 1234;

    private TcpListener listener;
    private bool isRunning = false;
    private bool isListening = false;

    public Button startButton;
    public Button stopButton;
    public Button startCalibrationButton;
    public Button stopCalibrationButton;
    public Button calibrateButton;

    public TextMeshProUGUI linearAccelX;
    public TextMeshProUGUI linearAccelY;
    public TextMeshProUGUI linearAccelZ;

    public TextMeshProUGUI gyroX;
    public TextMeshProUGUI gyroY;
    public TextMeshProUGUI gyroZ;

    public Vector3 linearAccelData;
    public Vector3 gyroData;

    public GameObject _cube;

    //Benchmark variables
    public TextMeshProUGUI frequencyText;
    bool isFirstDataReceived = false;
    Stopwatch stopwatch = new Stopwatch();
    int dataCount = 0;

    //Calibration variables
    public TextMeshProUGUI calibrationText;

    private bool isCalibrating = false;
    private int currentAxis = 0;
    private float currentOffset = 0f;

    private const int numCalibrationSteps = 3;
    private const float rotationAmount = 180f;


    private bool startCalibrationButtonClicked = false;
    private bool stopCalibrationButtonClicked = false;

    private void Start()
    {
        startButton.onClick.AddListener(StartServer);
        stopButton.onClick.AddListener(StopServer);
        calibrateButton.onClick.AddListener(Calibrate);

        startCalibrationButton.interactable = false;
        stopCalibrationButton.interactable = false;
        startCalibrationButton.onClick.AddListener(() => startCalibrationButtonClicked = true);
        stopCalibrationButton.onClick.AddListener(() => stopCalibrationButtonClicked = true);
    }

    private void OnDestroy()
    {
        StopServer();
    }

    private void StartServer()
    {
        if (!isListening)
        {
            listener = new TcpListener(IPAddress.Any, port);
            isRunning = true;
            StartCoroutine(ListenForClients());
            Debug.Log("Server started on port " + port);
            isListening = true;
        }
    }

    private void StopServer()
    {
        isRunning = false;
        listener.Stop();
        Debug.Log("Server stopped");
        isListening = false;
    }

    private IEnumerator ListenForClients()
    {
        listener.Start();

        while (isRunning)
        {
            if (listener.Pending())
            {
                TcpClient client = listener.AcceptTcpClient();
                Debug.Log("Client connected");

                // Start the HandleClientCoroutine coroutine
                StartCoroutine(HandleClientCoroutine(client));
            }

            yield return new WaitForSeconds(0.1f);
        }

        listener.Stop();
        Debug.Log("Server stopped");
        isListening = false;
    }

    private IEnumerator HandleClientCoroutine(TcpClient client)
    {
        Debug.Log("This has been started");
        NetworkStream stream = client.GetStream();
        byte[] buffer = new byte[24];
        int bytesRead = 0;

        while (isRunning && client.Connected)
        {
            try
            {
                if (stream.DataAvailable)
                {
                    // If this is the first data received, set the flag and start the timer
                    if (!isFirstDataReceived)
                    {
                        isFirstDataReceived = true;
                        stopwatch.Start();
                    }

                    bytesRead = stream.Read(buffer, 0, buffer.Length);

                    // Convert the byte array to float values
                    float[] sensorData = new float[6];
                    for (int i = 0; i < sensorData.Length; i++)
                    {
                        sensorData[i] = BitConverter.ToSingle(buffer, i * sizeof(float));
                    }

                    
                    // Update the TextMeshPro objects with the received data
                    linearAccelX.text = string.Format("{0:F2}", sensorData[0]);
                    linearAccelY.text = string.Format("{0:F2}", sensorData[1]);
                    linearAccelZ.text = string.Format("{0:F2}", sensorData[2]);

                    linearAccelData = new Vector3(sensorData[0], sensorData[1], sensorData[2]);

                    gyroX.text = string.Format("{0:F2}", sensorData[3]);
                    gyroY.text = string.Format("{0:F2}", sensorData[4]);
                    gyroZ.text = string.Format("{0:F2}", sensorData[5]);

                    gyroData = new Vector3(sensorData[3], sensorData[4], sensorData[5]);

                    // If you want to measure the frequency of data received, you can update a counter
                    // and calculate the frequency at regular intervals (e.g. every 1 second)
                    dataCount++;

                    // If 1 second has elapsed, calculate the frequency and reset the counter and timer
                    if (stopwatch.ElapsedMilliseconds >= 1000)
                    {
                        float frequency = dataCount / ((float)stopwatch.ElapsedMilliseconds / 1000);

                        frequencyText.text = frequency.ToString();
                        Debug.Log("Data frequency: " + frequency + " Hz");

                        dataCount = 0;
                        stopwatch.Reset();
                        stopwatch.Start();
                    }
                }
                else
                {
                    //Debug.Log("No data available");
                }
            }
            catch (Exception e)
            {
                Debug.LogError("Error reading from client: " + e);
                break;
            }

            yield return new WaitForSeconds(0.01f);
        }

        if (stream != null)
        {
            stream.Close();
        }
        if (client != null)
        {
            client.Close();
        }

        Debug.Log("Client disconnected");
    }

    private void Update()
    {
        Quaternion gyroRotation = Quaternion.Euler(gyroData.y, -gyroData.x, gyroData.z);
        _cube.transform.rotation *= gyroRotation;
    }

    private IEnumerator CalibrateCoroutine()
    {
        isCalibrating = true;



        for (int i = 0; i < numCalibrationSteps; i++)
        {
            // Display current axis to user
            currentAxis = i;
            calibrationText.text = $"Calibrate axis {currentAxis + 1}";

            startCalibrationButton.interactable = true;

            // Wait for user to click Start Calibration button
            yield return new WaitUntil(() => startCalibrationButtonClicked);

            startCalibrationButtonClicked = false;
            startCalibrationButton.interactable = false;

            // 
            Debug.Log("Starting calibration for axis " + currentAxis.ToString());

            stopCalibrationButton.interactable = true;

            // Wait for user to click Stop Calibration button
            yield return new WaitUntil(() => stopCalibrationButtonClicked);

            stopCalibrationButtonClicked = false;
            stopCalibrationButton.interactable = false;

            // 
            Debug.Log("Stopping calibration for axis " + currentAxis.ToString());
        }

        // Calibration complete
        currentAxis = -1;
        currentOffset = 0f;
        calibrationText.text = "Calibration complete";
        isCalibrating = false;
    }

    private void Calibrate()
    {
        if (!isCalibrating)
        {
            StartCoroutine(CalibrateCoroutine());
        }
    }

}