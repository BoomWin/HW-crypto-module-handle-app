package com.example.tcp_ip_client;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    // USB related constants
    private static final int USB_PERMISSION_REQUEST = 0;
    private static final String ACTION_USB_PERMISSION = "com.example.tcp_ip_client.USB_PERMISSION";
    
    // UI elements
    private EditText editTextServerIP, editTextServerPort, editTextMessage;
    private Button buttonConnect, buttonDisconnect, buttonInit;
    private Button buttonClear;
    private Button buttonGetVersion;
    private TextView textViewStatus, textViewReceived;
    private TCPClient tcpClient;
    private Handler mainHandler;

    // USB serial related variables
    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private CryptoSerial cryptoSerial;
    private CryptoModule cryptoModule;
    private Button buttonUsbConnect, buttonUsbTest;

    // Authentication protocol related variables
    private Button buttonsendRandomButton;
    private Button buttonMacCalculate;
    private byte[] serverChallenge;
    // PSK related variables
    private Button buttonDataSave;
    private Button buttonDataBring;

    // Flag to track MAC response processing
    private volatile boolean macResponseProcessed = false;

    // Authentication result storage variable
    private String serverAuthResult = null;

    // Flag for waiting for server response
    private volatile boolean waitingForServerResponse = false;

    // Variable to manage message order
    private final Object messageLock = new Object();
    private boolean macSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TCP/IP UI elements initialization
        editTextServerIP = findViewById(R.id.editTextServerIP);
        editTextServerPort = findViewById(R.id.editTextServerPort);

        buttonConnect = findViewById(R.id.buttonConnect);
        buttonDisconnect = findViewById(R.id.buttonDisconnect);
        buttonInit = findViewById(R.id.button_hw_init);

        textViewStatus = findViewById(R.id.textViewStatus);
        textViewReceived = findViewById(R.id.textViewReceived);

        // USB serial UI elements initialization
        buttonUsbConnect = findViewById(R.id.buttonUsbConnect);
        buttonUsbTest = findViewById(R.id.buttonUsbTest);
        buttonClear = findViewById(R.id.buttonClear);
        buttonGetVersion = findViewById(R.id.buttonGetVersion);

        // PSK related buttons
        buttonDataSave = findViewById(R.id.saveDataButton);
        buttonDataBring = findViewById(R.id.bringDataButton);

        // Authentication protocol related
        buttonsendRandomButton = findViewById(R.id.sendRandomButton);
        // MAC calculation and transmission button setting
        buttonMacCalculate = findViewById(R.id.calculateMacButton);

        // Main handler initialization
        mainHandler = new Handler(Looper.getMainLooper());

        // USB manager initialization
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Register BroadcastReceiver to receive USB permission request results
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        // Set initial button status
        buttonGetVersion.setEnabled(false);

        // TCP/IP connection button event
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String serverIP = editTextServerIP.getText().toString();
                String serverPortStr = editTextServerPort.getText().toString();

                if (serverIP.isEmpty() || serverPortStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Enter server IP and port", Toast.LENGTH_SHORT).show();
                    return;
                }
                int serverPort = Integer.parseInt(serverPortStr);
                new ConnectTask().execute(serverIP, String.valueOf(serverPort));
            }
        });


        // TCP/IP disconnection button event
        buttonDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tcpClient != null) {
                    new DisconnectTask().execute();
                }
            }
        });

        // USB serial connection button event
        buttonUsbConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findSerialPortDevice();
            }
        });

        // USB serial test button event
        buttonUsbTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUsbTestButtonClick();
            }
        });

        // Hardware initialization button event
        buttonInit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findSerialPortDevice(); // Find USB serial device
            }
        });

        // Clear screen button event
        buttonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearMessageHistory();
            }
        });

        // Version information request button event
        buttonGetVersion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cryptoModule != null) {
                    // Request version information
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            cryptoModule.getVersion();
                        }
                    }).start();
                } else {
                    Toast.makeText(MainActivity.this, "Encryption module not initialized", Toast.LENGTH_SHORT).show();
                }
            }
        });


        // Random generation and transmission button setting
        buttonsendRandomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onGenerateRandomButtonClick();
            }
        });

        // MAC calculation and transmission button setting
        buttonMacCalculate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMacCalculateButtonClick();
            }
        });

        // PSK save button setting
        buttonDataSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveDataButtonClick();
            }
        });

        // PSK get button setting
        buttonDataBring.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBringDataButtonClick();
            }
        });

    }

    // Find USB serial device
    private void findSerialPortDevice() {
        // Get available driver list
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (availableDrivers.isEmpty()) {
            appendToMessageHistory("USB serial device not found");
            return;
        }

        // Use the first available driver
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        // Display device information
        appendToMessageHistory("Device found: " + device.getDeviceName() +
                "\nVendorId: " + device.getVendorId() +
                ", ProductId: " + device.getProductId());

        // Check permission and request
        if (!usbManager.hasPermission(device)) {
            appendToMessageHistory("Requesting USB permission...");
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
        } else {
            // If already has permission, proceed with connection
            connectToSerialPort(device, driver);
        }
    }

    // Connect to serial port
    private void connectToSerialPort(UsbDevice device, UsbSerialDriver driver) {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            appendToMessageHistory("Device connection failed");
            return;
        }

        try {
            // Use the first port (most devices have only one port)
            serialPort = driver.getPorts().get(0);
            serialPort.open(connection);

            // Set serial communication parameters (baud rate, data bits, stop bits, parity)
            serialPort.setParameters(
                    115200,                       // Baud rate (confirmed value from log)
                    UsbSerialPort.DATABITS_8,   // Data bits
                    UsbSerialPort.STOPBITS_1,   // Stop bits
                    UsbSerialPort.PARITY_NONE   // Parity
            );

            // CryptoSerial and CryptoModule initialization
            cryptoSerial = new CryptoSerial(serialPort);
            cryptoModule = new CryptoModule(this, cryptoSerial); // Context passed

            // Set message callback
            cryptoModule.setMessageCallback(new CryptoModule.MessageCallback() {
                @Override
                public void onMessage(String message) {
                    // Update TextView in UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            appendToMessageHistory(message);
                        }
                    });
                }
            });
            appendToMessageHistory("Serial port connection successful");
            buttonUsbTest.setEnabled(true);
            buttonGetVersion.setEnabled(true);

            // Start thread for data reception (if needed) ??
//            startIoManager();

        } catch (IOException e) {
            appendToMessageHistory("Serial port setting error: " + e.getMessage());
            try {
                if (serialPort != null) {
                    serialPort.close();
                }
            } catch (IOException ignored) {}
            serialPort = null;
        }
    }

    // Start thread for data reception
    private void startIoManager() {
        // Start separate thread for data reception processing
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[1024];
                while (serialPort != null) {
                    try {
                        int len = serialPort.read(buffer, 1000);
                        if (len > 0) {
                            final byte[] data = new byte[len];
                            System.arraycopy(buffer, 0, data, 0, len);

                            // Process data in UI thread
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    processReceivedData(data);
                                }
                            });
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Data reception error", e);
                        break;
                    }
                }
            }
        }).start();
    }

    // Process received data
    private void processReceivedData(byte[] data) {
        // Convert to hexadecimal string
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        appendToMessageHistory("Data received: " + sb.toString());
    }

    // LEA-128-ECB encryption test
    private void testLeaEncryption() {
        if (cryptoModule == null) {
            appendToMessageHistory("Encryption module not initialized");
            return;
        }

        try {
            // 로그에서 보인 키 값 사용
            byte[] key = CryptoModule.hexStringToBytes("00112233445566778899AABBCCDDEEFF");

            // 로그에서 보인 평문 사용
            byte[] data = CryptoModule.hexStringToBytes("80000000000000000000000000000000");


            appendToMessageHistory("LEA-128-ECB encryption test started");

            // 암호화 수행
            byte[] encrypted = cryptoModule.encryptLea128Ecb(key, data);

            if (encrypted != null) {
                // Convert to hexadecimal string for display
                StringBuilder sb = new StringBuilder();
                for (byte b : encrypted) {
                    sb.append(String.format("%02X", b));
                }
                appendToMessageHistory("LEA-128-ECB encryption [16] = " + sb.toString());

                // 결과 비교 예상 결과 vs 수행결과
                String expectedResult = "C92ED5DDB2448EC936CA33088D204032";

                if (sb.toString().equalsIgnoreCase(expectedResult)) {
                    appendToMessageHistory("Encryption successful (expected result matches)");
                } else {
                    appendToMessageHistory("Encryption result does not match expected result");
                    appendToMessageHistory("Expected result: " + expectedResult);
                }
            } else {
                appendToMessageHistory("Encryption failed");
            }
            appendToMessageHistory("==========================================");
        } catch (Exception e) {
            appendToMessageHistory("Encryption error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 시리얼 포트 닫기
    private void closeSerialPort() {
        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Serial port closing error", e);
            }
            serialPort = null;
        }

        if (cryptoModule != null) {
            cryptoModule.close();
            cryptoModule = null;
        }

        cryptoSerial = null;
        buttonUsbTest.setEnabled(false);
        appendToMessageHistory("USB connection ended");
    }

    // USB event receiver
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // If permission is granted, connect to the device
                            List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
                            for (UsbSerialDriver driver : drivers) {
                                if (driver.getDevice().equals(device)) {
                                    appendToMessageHistory("USB permission obtained");
                                    connectToSerialPort(device, driver);
                                    break;
                                }
                            }
                        }
                    } else {
                        appendToMessageHistory("USB permission denied");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                appendToMessageHistory("USB device connected");
                findSerialPortDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && serialPort != null &&
                        device.equals(serialPort.getDriver().getDevice())) {
                    appendToMessageHistory("USB device detached");
                    closeSerialPort();
                }
            }
        }
    };

    // TCP/IP status UI update
    private void updateConnectionStatus(boolean isConnected) {
        textViewStatus.setText("Connection status: " + (isConnected ? "Connected" : "Not connected"));
        buttonConnect.setEnabled(!isConnected);
        buttonDisconnect.setEnabled(isConnected);
    }

    // Append to message history
    private void appendToMessageHistory(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                // Responses starting with RS are emphasized
                if (message.startsWith("RS")) {
                    textViewReceived.append("\n" + message);
                }
                // Encrypted results are emphasized
                else if (message.contains("encryption [16]")) {
                    textViewReceived.append("\n\n==== Encrypted Result ====\n" + message + "\n====================\n");
                }
                else {
                    textViewReceived.append("\n" + message);
                }

                // Scroll to bottom
                final int scrollAmount = textViewReceived.getLayout().getLineTop(textViewReceived.getLineCount()) - textViewReceived.getHeight();
                if (scrollAmount > 0) {
                    textViewReceived.scrollTo(0, scrollAmount);
                }
            }
        });
    }

    // AsyncTask for TCP/IP connection
    private class ConnectTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            String serverIP = params[0];
            int serverPort = Integer.parseInt(params[1]);

            tcpClient = new TCPClient(serverIP, serverPort, new TCPClient.OnMessageReceviedListener() {
                @Override
                public void onMessageReceived(String message) {
                    // Process message in UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleServerMessage(message);
                        }
                    });
                }
            });

            boolean connected = tcpClient.connect();
            if (connected) {
                tcpClient.startListening();
            }
            return connected;
        }

        @Override
        protected void onPostExecute(Boolean isConnected) {
            if (isConnected) {
                Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(MainActivity.this, "Server connection failed", Toast.LENGTH_SHORT).show();
            }
            updateConnectionStatus(isConnected);
        }
    }

    // AsyncTask for sending message to TCP/IP
    private class SendMessageTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String message = params[0];
            if (tcpClient != null) {
                tcpClient.sendMessage(message);
            }
            return null;
        }
    }

    // AsyncTask for disconnecting from TCP/IP
    private class DisconnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (tcpClient != null) {
                tcpClient.disconnect();
                tcpClient = null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            updateConnectionStatus(false);
            Toast.makeText(MainActivity.this, "Connection ended", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // End TCP/IP connection
        if (tcpClient != null) {
            tcpClient.disconnect();
        }

        // End USB serial connection
        closeSerialPort();

        // Unregister BroadcastReceiver
        unregisterReceiver(usbReceiver);
    }

    // Clear message history
    private void clearMessageHistory() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewReceived.setText("");
            }
        });
    }


    // Random generation button click event handler
    private void onGenerateRandomButtonClick() {
        try {
            // Generate 16-byte (128-bit) random data
            SecureRandom secureRandom = new SecureRandom();
            byte[] randomData = new byte[16];
            secureRandom.nextBytes(randomData);
            
            // Convert random data to hexadecimal string
            String randomHex = bytesToHex(randomData).toUpperCase();
            
            // Update UI and log output
            appendToMessageHistory("===== Authentication Protocol Started =====");
            appendToMessageHistory("1️⃣ NonceC generated: " + randomHex);
            Log.d("MainActivity", "Generated NonceC: " + randomHex);
            
            // Save NonceC to TCP client
            if (tcpClient != null) {
                tcpClient.setClientNonce(randomData);
            }
            
            // Send NonceC to server - direct handling instead of AsyncTask
            new Thread(() -> {
                try {
                    // 출력 영어로 논문
                    if (tcpClient != null && tcpClient.isConnected()) {
                        // First display message in UI
                        runOnUiThread(() -> {
                            appendToMessageHistory("2️⃣ Requesting Challenge from server (sending NonceC...)");
                        });
                        
                        // Send NonceC
                        tcpClient.sendNonceC(randomData);
                        
                        // Enable MAC calculation button after NonceC is sent
                        runOnUiThread(() -> {
                            buttonMacCalculate.setEnabled(true);
                        });
                        
                        // Don't display completion message (message 3 will be displayed)
                        Log.d("MainActivity", "NonceC transmission completed");
                    } else {
                        runOnUiThread(() -> {
                            appendToMessageHistory("❌ NonceC transmission failed: Server connection lost");
                        });
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Message transmission error", e);
                    runOnUiThread(() -> {
                        appendToMessageHistory("❌ NonceC transmission error: " + e.getMessage());
                    });
                }
            }).start();
            
        } catch (Exception e) {
            // Detailed error logging
            Log.e("MainActivity", "Random generation error", e);
            appendToMessageHistory("❌ Random generation error: " + e.getMessage());
            
            // Stack trace output (for debugging)
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.e("MainActivity", "Stack trace: " + sw.toString());
        }
    }


    // MAC calculation and transmission button click event handler
    private void onMacCalculateButtonClick() {
        // Disable button (prevent duplicate clicks)
        buttonMacCalculate.setEnabled(false);
        
        // Initialize state
        synchronized (messageLock) {
            macSent = false;
            serverAuthResult = null;
        }
        
        // Perform MAC calculation and transmission in a separate thread
        new Thread(() -> {
            try {
                // PSK (Pre-Shared Key) - Use the same PSK as the server
                String pskHex = "306E538A29ADAB6E8FDD92C02101E9C2306E538A29ADAB6E8FDD92C02101E9C2";
                byte[] psk = hexStringToBytes(pskHex);
                
                if (psk == null) {
                    runOnUiThread(() -> appendToMessageHistory("❌ PSK conversion failed"));
                    return;
                }
                
                // Get client NonceC (previously generated random data)
                byte[] nonceC = tcpClient.getClientNonce();
                
                if (nonceC == null) {
                    runOnUiThread(() -> appendToMessageHistory("❌ Failed to get NonceC"));
                    return;
                }
                
                if (serverChallenge == null) {
                    runOnUiThread(() -> appendToMessageHistory("❌ Server NonceS is missing"));
                    return;
                }
                
                // Combine NonceC + NonceS (in the same order as the server)
                byte[] combinedData = new byte[nonceC.length + serverChallenge.length];
                System.arraycopy(nonceC, 0, combinedData, 0, nonceC.length);
                System.arraycopy(serverChallenge, 0, combinedData, nonceC.length, serverChallenge.length);
                
                // HMAC calculation
                byte[] macValue = null;
                try {
                    Mac mac = Mac.getInstance("HmacSHA256");
                    SecretKeySpec secretKeySpec = new SecretKeySpec(psk, "HmacSHA256");
                    mac.init(secretKeySpec);
                    macValue = mac.doFinal(combinedData);
                } catch (Exception e) {
                    Log.e("MainActivity", "HMAC calculation error", e);
                    runOnUiThread(() -> appendToMessageHistory("❌ HMAC calculation error: " + e.getMessage()));
                    return;
                }
                
                // Convert MAC value to hexadecimal string (uppercase)
                String macHex = bytesToHex(macValue).toUpperCase();
                Log.d("MainActivity", "Calculated MAC: " + macHex);
                
                // Update UI
                runOnUiThread(() -> appendToMessageHistory("4️⃣ Calculated MAC: " + macHex));
                
                // Create message (formatted for server)
                String message = "HMAC: " + macHex;
                
                // Send to server
                tcpClient.sendMessage(message);
                
                // Set macSent flag and display message
                synchronized (messageLock) {
                    runOnUiThread(() -> appendToMessageHistory("5️⃣ MAC value sent to server"));
                    macSent = true;
                    messageLock.notifyAll();
                }
                
                // Wait for server response (max 5 seconds)
                synchronized (messageLock) {
                    if (serverAuthResult == null) {
                        try {
                            messageLock.wait(5000);
                        } catch (InterruptedException e) {
                            Log.e("MainActivity", "Interrupted while waiting for server response", e);
                        }
                    }
                    
                    // Check if we received a response
                    if (serverAuthResult != null) {
                        final String result = serverAuthResult;
                        runOnUiThread(() -> {
                            appendToMessageHistory("6️⃣ Server response received");
                            if (result.equalsIgnoreCase("SUCCESS")) {
                                appendToMessageHistory("7️⃣ Authentication result: ✅ Success");
                                appendToMessageHistory("===== Authentication Protocol Completed =====");
                            } else {
                                appendToMessageHistory("7️⃣ Authentication result: ❌ Failed");
                                appendToMessageHistory("===== Authentication Protocol Failed =====");
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            appendToMessageHistory("❌ No response from server (timeout)");
                            appendToMessageHistory("===== Authentication Protocol Failed =====");
                        });
                    }
                }
                
            } catch (Exception e) {
                Log.e("MainActivity", "MAC calculation and transmission error", e);
                runOnUiThread(() -> {
                    appendToMessageHistory("❌ MAC calculation and transmission error: " + e.getMessage());
                    appendToMessageHistory("===== Authentication Protocol Failed =====");
                });
            } finally {
                // Re-enable button
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    buttonMacCalculate.setEnabled(true);
                }, 2000);
            }
        }).start();
    }

    // Server message handler
    private void handleServerMessage(String message) {
        Log.d("MainActivity", "Server message received: '" + message + "'");

        try {
            // Check message content precisely (consider spaces, newlines, etc.)
            String trimmedMessage = message.trim();
            Log.d("MainActivity", "Processing message (trimmed): '" + trimmedMessage + "'");
            
            // NonceS message format check
            if (trimmedMessage.contains("NonceS")) {
                // Find colon position
                int colonIndex = trimmedMessage.indexOf(":");
                if (colonIndex == -1) {
                    appendToMessageHistory("❌ Invalid NonceS format");
                    return;
                }
                
                // Extract NonceS (part after colon)
                String nonceS = trimmedMessage.substring(colonIndex + 1).trim();
                Log.d("MainActivity", "Extracted NonceS: " + nonceS);
                
                // Convert hex string to byte array
                serverChallenge = hexStringToBytes(nonceS);
                
                if (serverChallenge == null) {
                    appendToMessageHistory("❌ NonceS conversion failed");
                    return;
                }
                
                // Display NonceS received message
                appendToMessageHistory("3️⃣ Received NonceS from server: " + nonceS);
            }
            // SUCCESS or FAILED message handling
            else if (trimmedMessage.equalsIgnoreCase("SUCCESS") || trimmedMessage.equalsIgnoreCase("FAILED")) {
                // Store authentication result
                synchronized (messageLock) {
                    serverAuthResult = trimmedMessage;
                    messageLock.notifyAll();
                }
            }
            // Other messages
            else {
                appendToMessageHistory("Server: " + trimmedMessage);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Message processing error", e);
            appendToMessageHistory("❌ Message processing error: " + e.getMessage());
        }
    }

    /**
     * Convert hexadecimal string to byte array
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.length() == 0) {
            Log.e("MainActivity", "hexStringToBytes: Input is null or empty string");
            return null;
        }
        
        // Remove spaces
        hexString = hexString.replaceAll("\\s", "");
        int len = hexString.length();
        
        if (len % 2 != 0) {
            Log.e("MainActivity", "hexStringToBytes: Incorrect 16-bit hexadecimal string length: " + len);
            return null;
        }
        
        byte[] bytes = new byte[len / 2];
        
        try {
            for (int i = 0; i < len; i += 2) {
                bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                        + Character.digit(hexString.charAt(i + 1), 16));
            }
            return bytes;
        } catch (Exception e) {
            Log.e("MainActivity", "hexStringToBytes conversion error", e);
            return null;
        }
    }

    /**
     * Convert byte array to hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // USB serial encryption module test button click event handler
    private void onUsbTestButtonClick() {
        if (cryptoModule == null) {
            appendToMessageHistory("❌ Encryption module not initialized");
            return;
        }
        
        // Perform encryption module test in a separate thread
        new Thread(() -> {
            try {
                // Update UI
                runOnUiThread(() -> {
                    appendToMessageHistory("===== Encryption Module Test Started =====");
                    appendToMessageHistory("1️⃣ Encryption module connection check in progress...");
                });
                
                // Request version information
                byte[] versionInfo = cryptoModule.getVersion();
                
                if (versionInfo != null) {
                    String versionHex = bytesToHex(versionInfo);
                    runOnUiThread(() -> {
                        appendToMessageHistory("2️⃣ Version information received successfully: " + versionHex);
                    });
                } else {
                    runOnUiThread(() -> {
                        appendToMessageHistory("❌ Version information request failed");
                        appendToMessageHistory("===== Encryption Module Test Failed =====");
                    });
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Encryption module test error", e);
                runOnUiThread(() -> {
                    appendToMessageHistory("❌ Encryption module test error: " + e.getMessage());
                    appendToMessageHistory("===== Encryption Module Test Failed =====");
                });
            }
        }).start();
    }

    // PSK save button click event handler
    private void onSaveDataButtonClick() {
        if (cryptoModule == null) {
            appendToMessageHistory("❌ Encryption module not initialized");
            return;
        }
        
        // Perform PSK save in a separate thread
        new Thread(() -> {
            try {
                // Update UI
                runOnUiThread(() -> {
                    appendToMessageHistory("===== PSK Save Started =====");
                    appendToMessageHistory("1️⃣ PSK data preparation in progress...");
                });
                
                // Prepare PSK data (example)
                String pskHex = "306E538A29ADAB6E8FDD92C02101E9C2306E538A29ADAB6E8FDD92C02101E9C2";
                byte[] pskData = hexStringToBytes(pskHex);
                
                if (pskData == null) {
                    runOnUiThread(() -> appendToMessageHistory("❌ PSK data conversion failed"));
                    return;
                }
                
                runOnUiThread(() -> {
                    appendToMessageHistory("2️⃣ PSK data: " + pskHex);
                    appendToMessageHistory("3️⃣ Requesting PSK save to encryption module...");
                });
                
                // Request PSK save
                boolean result = cryptoModule.setpskData(pskData);
                
                if (result) {
                    runOnUiThread(() -> {
                        appendToMessageHistory("4️⃣ PSK save successful");
                        appendToMessageHistory("===== PSK Save Completed =====");
                    });
                } else {
                    runOnUiThread(() -> {
                        appendToMessageHistory("❌ PSK save failed");
                        appendToMessageHistory("===== PSK Save Failed =====");
                    });
                }
            } catch (Exception e) {
                Log.e("MainActivity", "PSK save error", e);
                runOnUiThread(() -> {
                    appendToMessageHistory("❌ PSK save error: " + e.getMessage());
                    appendToMessageHistory("===== PSK Save Failed =====");
                });
            }
        }).start();
    }

    // PSK get button click event handler
    private void onBringDataButtonClick() {
        if (cryptoModule == null) {
            appendToMessageHistory("❌ Encryption module not initialized");
            return;
        }
        
        // Perform PSK get in a separate thread
        new Thread(() -> {
            try {
                // Update UI
                runOnUiThread(() -> {
                    appendToMessageHistory("===== PSK Get Started =====");
                    appendToMessageHistory("1️⃣ Requesting PSK from encryption module...");
                });
                
                // Request PSK get
                byte[] pskData = cryptoModule.getpskData();
                
                if (pskData != null) {
                    String pskHex = bytesToHex(pskData);
                    runOnUiThread(() -> {
                        appendToMessageHistory("2️⃣ PSK get successful");
                        appendToMessageHistory("3️⃣ PSK data: " + pskHex);
                        appendToMessageHistory("===== PSK Get Completed =====");
                    });
                } else {
                    runOnUiThread(() -> {
                        appendToMessageHistory("❌ PSK get failed");
                        appendToMessageHistory("===== PSK Get Failed =====");
                    });
                }
            } catch (Exception e) {
                Log.e("MainActivity", "PSK get error", e);
                runOnUiThread(() -> {
                    appendToMessageHistory("❌ PSK get error: " + e.getMessage());
                    appendToMessageHistory("===== PSK Get Failed =====");
                });
            }
        }).start();
    }

}