using System;
using System.Collections;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using TMPro;
using Unity.VisualScripting;
using UnityEngine;
using UnityEngine.UI;

public class SensorServer : MonoBehaviour
{
    public int port = 1234;

    private TcpListener listener;
    private bool isRunning = false;
    private bool isListening = false;

    public Button startButton;
    public Button stopButton;

    public TextMeshProUGUI linearAccelX;
    public TextMeshProUGUI linearAccelY;
    public TextMeshProUGUI linearAccelZ;

    public TextMeshProUGUI gyroX;
    public TextMeshProUGUI gyroY;
    public TextMeshProUGUI gyroZ;

    public Vector3 linearAccelData;
    public Vector3 gyroData;

    public GameObject _cube;

    private void Start()
    {
        startButton.onClick.AddListener(StartServer);
        stopButton.onClick.AddListener(StopServer);
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
        byte[] buffer = new byte[1024];
        int bytesRead = 0;

        while (isRunning && client.Connected)
        {
            try
            {
                if (stream.DataAvailable)
                {
                    bytesRead = stream.Read(buffer, 0, buffer.Length);

                    // Convert the byte array to a string using ASCII encoding
                    string data = Encoding.ASCII.GetString(buffer, 0, bytesRead);

                    Debug.Log(data);

                    // Check if the received data is valid
                    if (!IsValidData(data))
                    {
                        Debug.LogWarning("Received data is invalid");
                        continue;
                    }

                    // Split the string into 6 parts using a separator (e.g. ",")
                    string[] parts = data.Split(',');

                    // Update the TextMeshPro objects with the received data
                    linearAccelX.text = string.Format("{0:F2}", float.Parse(parts[0]));
                    linearAccelY.text = string.Format("{0:F2}", float.Parse(parts[1]));
                    linearAccelZ.text = string.Format("{0:F2}", float.Parse(parts[2]));

                    linearAccelData = new Vector3(float.Parse(parts[0]), float.Parse(parts[1]), float.Parse(parts[2]));

                    gyroX.text = string.Format("{0:F2}", float.Parse(parts[3]));
                    gyroY.text = string.Format("{0:F2}", float.Parse(parts[4]));
                    gyroZ.text = string.Format("{0:F2}", float.Parse(parts[5]));

                    gyroData = new Vector3(float.Parse(parts[3]), float.Parse(parts[4]), float.Parse(parts[5])); 
                }
                else
                {
                    Debug.Log("No data available");
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


    private bool IsValidData(string data)
    {
        // Check if the data contains 6 parts separated by commas
        string[] parts = data.Split(',');
        if (parts.Length != 6)
        {
            return false;
        }

        // Check if each part can be parsed to a float
        float value;
        if (!float.TryParse(parts[0], out value))
        {
            return false;
        }
        if (!float.TryParse(parts[1], out value))
        {
            return false;
        }
        if (!float.TryParse(parts[2], out value))
        {
            return false;
        }
        if (!float.TryParse(parts[3], out value))
        {
            return false;
        }
        if (!float.TryParse(parts[4], out value))
        {
            return false;
        }
        if (!float.TryParse(parts[5], out value))
        {
            return false;
        }

        // Data is valid
        return true;
    }

}