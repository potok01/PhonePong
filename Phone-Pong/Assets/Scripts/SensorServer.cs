using System;
using System.Collections;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using TMPro;
using UnityEditor.Experimental.GraphView;
using UnityEngine;
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
    public Button startGyroCalibrationButton;
    public Button stopGyroCalibrationButton;
    public Button calibrateButton;
    public Button resetRotationButton;
    public Button lockToPhoneButton;

    public TextMeshProUGUI sensorReadings;

    public Vector3 linearAccelData;
    public Vector3 gyroData;
    public Vector3 magData;

    public GameObject _cube;

    //Benchmark variables
    public TextMeshProUGUI frequencyText;
    bool isFirstDataReceived = false;
    Stopwatch stopwatch = new Stopwatch();
    int dataCount = 0;

    //Gyro calibration variables
    private const int numGyroCalibrationSteps = 3;
    private const float rotationAmount = 180f;
    public TextMeshProUGUI gyroCalibrationText;
    private bool startGyroCalibrationButtonClicked = false;
    private bool stopGyroCalibrationButtonClicked = false;
    private bool isCalibratingGyro = false;
    private int currentGyroAxis = 0;
    public float[] calibratedGyroScaleFactors = { 0.1f, 0.1f, 0.1f };

    //Accelerometer calibration variables
    private const int numAccelCalibrationSteps = 3;
    public TextMeshProUGUI accelCalibrationText;
    private bool startAccelCalibrationButtonClicked = false;
    private bool stopAccelCalibrationButtonClicked = false;
    private bool isCalibratingAccel = false;
    private int currentAccelAxis = 0;
    public float[] calibratedAccelScaleFactors = { 0.1f, 0.1f, 0.1f };

    public Vector3 linearAcceleration = new Vector3(0,0,0);
    public Vector3 linearVelocity = new Vector3(0, 0, 0);
    public Quaternion orientation;
    public float speedMultiplier = 1.0f;
    private void Start()
    {
        startButton.onClick.AddListener(StartServer);
        stopButton.onClick.AddListener(StopServer);
        calibrateButton.onClick.AddListener(CalibrateGyro);
        resetRotationButton.onClick.AddListener(ResetRotation);
        lockToPhoneButton.onClick.AddListener(LockToPhone);

        startGyroCalibrationButton.interactable = false;
        stopGyroCalibrationButton.interactable = false;
        startGyroCalibrationButton.onClick.AddListener(() => startGyroCalibrationButtonClicked = true);
        stopGyroCalibrationButton.onClick.AddListener(() => stopGyroCalibrationButtonClicked = true);
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
        byte[] buffer = new byte[28];
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
                    float[] sensorData = new float[7];
                    for (int i = 0; i < sensorData.Length; i++)
                    {
                        sensorData[i] = BitConverter.ToSingle(buffer, i * sizeof(float));
                    }

                    HandleSensorData(sensorData);

                    string sensorText = "";

                    // Update the TextMeshPro objects with the received data
                    for (int i = 0; i < sensorData.Length; i++)
                    {
                        sensorText += string.Format("{0:F2}", sensorData[i]);
                        sensorText += " ";
                    }

                    linearAcceleration = new Vector3(sensorData[0], sensorData[1], sensorData[2]);
                    orientation = new Quaternion(sensorData[3], sensorData[4], sensorData[5], sensorData[6]);

                    // Show sensor readings
                    sensorReadings.text = sensorText;

                    // If you want to measure the frequency of data received, you can update a counter
                    // and calculate the frequency at regular intervals (e.g. every 1 second)
                    dataCount++;

                    // If 1 second has elapsed, calculate the frequency and reset the counter and timer
                    if (stopwatch.ElapsedMilliseconds >= 1000)
                    {
                        float frequency = dataCount / ((float)stopwatch.ElapsedMilliseconds / 1000);

                        frequencyText.text = frequency.ToString();
                        // Debug.Log("Data frequency: " + frequency + " Hz");

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
        _cube.transform.localRotation = orientation;

        linearVelocity += linearAcceleration * Time.deltaTime;
        _cube.transform.position += linearVelocity * speedMultiplier * Time.deltaTime;
    }

    private void HandleSensorData(float[] sensorData)
    {

    }
    private IEnumerator CalibrateGyroCoroutine()
    {
        isCalibratingGyro = true;

        for (int axis = 0; axis < numGyroCalibrationSteps; axis++)
        {

            // Display current axis to user
            currentGyroAxis = axis;
            gyroCalibrationText.text = $"Calibrate axis {currentGyroAxis + 1}";

            startGyroCalibrationButton.interactable = true;

            // Wait for user to click Start Calibration button
            yield return new WaitUntil(() => startGyroCalibrationButtonClicked);
            ResetRotation();

            // Disable start
            startGyroCalibrationButtonClicked = false;
            startGyroCalibrationButton.interactable = false;

            // Enable stop
            stopGyroCalibrationButton.interactable = true;

            // Wait for user to click Stop Calibration button
            yield return new WaitUntil(() => stopGyroCalibrationButtonClicked);

            // Record cube final rotation
            Quaternion finalCubeRotation = _cube.transform.rotation;
            float finalAlongOneAxis = finalCubeRotation.eulerAngles[axis];
            Debug.Log(finalAlongOneAxis);

            //Disable Stop
            stopGyroCalibrationButtonClicked = false;
            stopGyroCalibrationButton.interactable = false;

            // Calculate difference in angles
            calibratedGyroScaleFactors[axis] = 1f / ((finalAlongOneAxis)/ (rotationAmount * calibratedGyroScaleFactors[axis]));
        }

        // Calibration complete
        currentGyroAxis = -1;
        gyroCalibrationText.text = "Calibration complete";
        isCalibratingGyro = false;
    }

    private void CalibrateGyro()
    {
        if (!isCalibratingGyro)
        {
            StartCoroutine(CalibrateGyroCoroutine());
        }
    }

    public void ResetRotation()
    {
        _cube.transform.rotation = Quaternion.identity;
    }

    public void LockToPhone()
    {
        _cube.transform.rotation = new Quaternion(0f, -0.707106829f, 0.707106829f, 0f);
        Debug.Log("Hi");
    }

}