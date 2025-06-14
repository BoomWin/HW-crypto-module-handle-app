package com.example.tcp_ip_client;

import static android.content.ContentValues.TAG;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class TCPClient {
    private String serverIP;
    private int serverPort;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean isConnected = false;
    private OnMessageReceviedListener messageListener;
    
    // 서버로부터 받은 NonceS 값을 저장
    private byte[] serverNonce = null;
    // 서버로 받은 HMAC 값을 저장
    private byte[] serverHmac = null;
    // 클라이언트에서 생성한 NonceC 값을 저장
    private byte[] clientNonce = null;


    // 메시지 수신 리스너 인터페이스
    public interface OnMessageReceviedListener {
        void onMessageReceived(String message);
    }

    public TCPClient(String serverIP, int serverPort, OnMessageReceviedListener listener) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.messageListener = listener;
    }

    // 서버에 연결
    public boolean connect() {
        try {
            Log.d(TAG, "서버 연결 시도: " + serverIP + ":" + serverPort);
            socket = new Socket(serverIP, serverPort);
            Log.d(TAG, "소켓 연결 성공");
            
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Log.d(TAG, "입력 스트림 생성 성공");
            
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            Log.d(TAG, "출력 스트림 생성 성공");
            
            isConnected = true;
            Log.d(TAG, "서버 연결 완료");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "서버 연결 실패: " + e.getMessage(), e);
            e.printStackTrace();
            return false;
        }
    }

    // 메시지 전송
    public void sendMessage(String message) {
        if (out != null && !out.checkError()) {
            Log.d(TAG, "전송 시작: " + message);
            out.println(message);
            out.flush(); // 명시적으로 flush 호출
            
            try {
                // 잠시 대기하여 서버가 응답할 시간을 줌
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, "대기 중 인터럽트 발생", e);
            }
            
            Log.d(TAG, "전송 완료 및 flush 완료");
        } else {
            Log.e(TAG, "메시지 전송 실패: 출력 스트림 오류");
        }
    }
    
    // NonceC 전송 (RandomData)
    public void sendNonceC(byte[] nonceC) {
        if (nonceC == null || nonceC.length == 0) {
            Log.e(TAG, "NonceC 전송 실패: 데이터가 null 또는 빈 배열");
            return;
        }
        
        // NonceC를 16진수 문자열로 변환
        StringBuilder sb = new StringBuilder();
        for (byte b : nonceC) {
            sb.append(String.format("%02X", b));
        }
        String nonceHex = sb.toString();
        
        // 메시지 형식 구성
        String message = "NonceC : " + nonceHex;
        
        // 메시지 전송
        Log.d(TAG, "NonceC 전송: " + message);
        sendMessage(message);
        
        // 클라이언트 NonceC 저장
        this.clientNonce = nonceC;
    }

    // 메시지 수신 스레드 시작
    public void startListening() {
        if (!isConnected || socket == null) {
            Log.e(TAG, "수신 시작 실패: 연결되지 않음");
            return;
        }

        Log.d(TAG, "수신 스레드 시작");
        Thread receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "수신 대기 중...");
                    
                    // 소켓 타임아웃 설정 (10초로 증가)
                    socket.setSoTimeout(10000);
                    
                    BufferedReader localReader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                    
                    String message;
                    while (isConnected && socket != null && !socket.isClosed()) {
                        try {
                            message = localReader.readLine();
                            
                            if (message == null) {
                                Log.d(TAG, "수신된 메시지가 null입니다. 연결이 종료되었을 수 있습니다.");
                                continue;
                            }
                            
                            Log.d(TAG, "수신: '" + message + "'");
                            
                            // SUCCESS 메시지 특별 처리
                            if (message.contains("SUCCESS")) {
                                Log.d(TAG, "SUCCESS 메시지 감지됨! 즉시 처리합니다.");
                            }
                            
                            // 메시지 리스너에 전달
                            if (messageListener != null) {
                                messageListener.onMessageReceived(message);
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            // 타임아웃 발생 - 정상적인 상황, 다시 시도
                            Log.d(TAG, "수신 타임아웃, 다시 시도합니다.");
                            continue;
                        } catch (IOException e) {
                            if (isConnected) {
                                Log.e(TAG, "메시지 읽기 오류: " + e.getMessage(), e);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "수신 스레드 오류: " + e.getMessage(), e);
                } finally {
                    Log.d(TAG, "수신 스레드 종료");
                    isConnected = false;
                }
            }
        });
        receiveThread.setDaemon(true); // 데몬 스레드로 설정하여 앱 종료 시 자동 종료
        receiveThread.start();
    }

    // 메시지 전송 후 응답 대기 메서드 추가
    public String sendAndWaitForResponse(String message, int timeoutMs) {
        if (!isConnected || socket == null) {
            Log.e(TAG, "전송 실패: 연결되지 않음");
            return null;
        }
        
        final String[] response = {null};
        final boolean[] responseReceived = {false};
        
        // 임시 메시지 리스너 설정
        OnMessageReceviedListener originalListener = messageListener;
        messageListener = new OnMessageReceviedListener() {
            @Override
            public void onMessageReceived(String msg) {
                Log.d(TAG, "응답 수신: " + msg);
                response[0] = msg;
                responseReceived[0] = true;
                
                // 원래 리스너도 호출
                if (originalListener != null) {
                    originalListener.onMessageReceived(msg);
                }
            }
        };
        
        // 메시지 전송
        sendMessage(message);
        
        // 응답 대기
        long startTime = System.currentTimeMillis();
        while (!responseReceived[0] && System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, "응답 대기 중 인터럽트 발생", e);
                break;
            }
        }
        
        // 원래 리스너 복원
        messageListener = originalListener;
        
        return response[0];
    }

    // 연결 종료
    public void disconnect() {
        isConnected = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (out != null) {
            out.close();
        }
    }

    // 연결 상태 확인
    public boolean isConnected() {
        return isConnected && socket != null && socket.isConnected() && !socket.isClosed();
    }
    
    // 저장된 서버 NonceS 값 반환
    public byte[] getServerNonce() {
        return serverNonce;
    }

    // 저장된 서버 HMAC 값 반환
    public byte[] getServerHmac() {
        return serverHmac;
    }
    
    // 저장된 클라이언트 NonceC 값 반환
    public byte[] getClientNonce() {
        return this.clientNonce;
    }
    
    // 16진수 문자열을 바이트 배열로 변환
    private byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.length() == 0) {
            return new byte[0];
        }

        hexString = hexString.replaceAll("\\s", "");
        int len = hexString.length();
        byte[] bytes = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }

        return bytes;
    }
    
    // 타임아웃 설정 메서드 추가
    public void setTimeout(int timeout) {
        try {
            if (socket != null) {
                socket.setSoTimeout(timeout);
            }
        } catch (Exception e) {
            Log.e(TAG, "타임아웃 설정 오류: " + e.getMessage());
        }
    }

    // 메시지 리스너 getter
    public OnMessageReceviedListener getMessageListener() {
        return messageListener;
    }

    // 메시지 리스너 setter
    public void setMessageListener(OnMessageReceviedListener listener) {
        this.messageListener = listener;
    }

    // 클라이언트 NonceC 설정 메서드
    public void setClientNonce(byte[] nonce) {
        this.clientNonce = nonce;
        Log.d(TAG, "클라이언트 NonceC 설정 완료: 길이=" + (nonce != null ? nonce.length : 0));
    }
}
