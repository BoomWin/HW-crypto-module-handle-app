package com.example.tcp_ip_client;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class CryptoSerial {
    private static final String TAG = "CryptoSerial";
    private static final int TIMEOUT = 2000; // 2초 타임아웃

    private UsbSerialPort serialPort;

    public CryptoSerial(UsbSerialPort port) {
        this.serialPort = port;
    }

    /**
     * 요청을 보내고 응답을 받음
     * @param request 요청 데이터
     * @return 응답 데이터, 오류시 null
     */
    public byte[] sendRequest(byte[] request) {
        if (serialPort == null) {
            Log.e(TAG, "Serial port is not initialized");
            return null;
        }

        try {
            // 기존 데이터 비우기
            byte[] flushBuffer = new byte[1024];
            int bytesRead;
            
            // 시리얼 포트에서 데이터를 계속 읽어서 버퍼를 비움
            // 타임아웃을 짧게(10ms) 설정하여 빠르게 처리
            // 더 이상 읽을 데이터가 없을 때까지(bytesRead <= 0) 반복
            while ((bytesRead = serialPort.read(flushBuffer, 10)) > 0) {
                Log.d(TAG, "버퍼 비우기: " + bytesRead + "바이트 제거");
            }
            
            // 요청 전송
            Log.d(TAG, "요청 전송: " + bytesToHex(request));
            serialPort.write(request, TIMEOUT);
            
            // 응답 대기
            long startTime = System.currentTimeMillis();
            byte[] buffer = new byte[1024];
            byte[] response = null;
            
            while (System.currentTimeMillis() - startTime < TIMEOUT) {
                int len = serialPort.read(buffer, 100);
                if (len > 0) {
                    response = new byte[len];
                    System.arraycopy(buffer, 0, response, 0, len);
                    
                    // 데이터 로깅
                    Log.d(TAG, "데이터 수신: " + bytesToHex(response));
                    
                    break;  // 데이터를 받았으므로 루프 종료
                }
                
                // 짧은 대기 시간
                Thread.sleep(10);
            }
            
            if (response == null) {
                Log.e(TAG, "응답 타임아웃");
            }
            
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Error in sendRequest: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    /**
     * 시리얼 포트 닫기
     */
    public void close() {
        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing serial port", e);
            }
            serialPort = null;
        }
    }

    /**
     * 시리얼 포트 객체 반환
     * @return UsbSerialPort 객체
     */
    public UsbSerialPort getSerialPort() {
        return serialPort;
    }
}