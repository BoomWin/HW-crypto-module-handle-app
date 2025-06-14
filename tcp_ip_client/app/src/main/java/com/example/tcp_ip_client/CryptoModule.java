package com.example.tcp_ip_client;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.security.spec.ECField;
import java.util.concurrent.CancellationException;

public class CryptoModule {
    private static final String TAG = "CryptoModule";

    private CryptoSerial serial;
    private boolean isLoggedIn = false;

    // 메시지 콜백 인터페이스
    public interface MessageCallback {
        void onMessage(String message);
    }

    private MessageCallback messageCallback;

    // 명령 코드
    private static final byte CMD_VERSION = (byte)0x01; // Version 명령 코드
    private static final byte CMD_LEA_128_ECB_ENCRYPT = (byte)0x03; // 로그에서 확인된 명령 코드

    // 최대 데이터 크기
    private static final int MAX_DATA_SIZE = 1500;

    public CryptoModule(Context context, CryptoSerial serial) {
        this.serial = serial;
    }

    /**
     * 메시지 콜백 설정
     */
    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    /**
     * 메시지 출력
     */
    private void displayMessage(String message) {
        Log.d(TAG, message);
        if (messageCallback != null) {
            messageCallback.onMessage(message);
        }
    }

    /**
     * QSIM 장치 초기화
     * @return 성공 여부
     */
    public boolean init() {
        if (serial == null) {
            displayMessage("CryptoSerial 초기화 안됨.");
            return false;
        }

        try {
            // 버전 정보 요청
            byte[] version = getVersion();
            if (version == null) {
                displayMessage("버전 정보 얻어오기 실패");
                return false;
            }
            displayMessage("QSIM 초기화 성공");
            return true;
        } catch (Exception e) {
            displayMessage("초기화 에러 : " + e.getMessage());
            return false;
        }
    }

    /**
 * 버전 정보 조회
 * @return 버전 정보 바이트 배열, 오류시 null
 */
public byte[] getVersion() {
    if (serial == null) {
        displayMessage("암호모듈 초기화되지 않음.");
        return null;
    }

    try {
        displayMessage("==========================================");
        displayMessage("VERSION");

        // 버전 정보 요청
        byte[] versionRequest = new byte[8];
        versionRequest[0] = (byte)0xD4;
        versionRequest[1] = CMD_VERSION;
        versionRequest[2] = (byte)0x00;
        versionRequest[3] = (byte)0x00;
        versionRequest[4] = (byte)0x00;
        versionRequest[5] = (byte)0x65;
        versionRequest[6] = (byte)0xE0;
        versionRequest[7] = (byte)0xD8;

        displayMessage("RQ [8]  = " + bytesToHex(versionRequest));
        
        // 요청 전송 및 응답 수신
        byte[] versionResponse = serial.sendRequest(versionRequest);
        
        // 응답 검증
        if (versionResponse == null) {
            displayMessage("Version Request Failed: 응답이 null입니다");
            return null;
        }
        
        if (versionResponse.length < 5) {
            displayMessage("Version Request Failed: 응답 길이가 부족합니다 (최소 5바이트 필요)");
            return null;
        }
        
        displayMessage("RS [" + versionResponse.length + "] = " + bytesToHex(versionResponse));

        // 버전 정보 길이 추출
        int versionLength = versionResponse[4] & 0xFF;
        displayMessage("버전 정보 길이: " + versionLength + " 바이트");
        
        // 버전 정보가 있고 응답 길이가 충분한지 확인
        if (versionLength <= 0) {
            displayMessage("Invalid version response: 버전 길이가 0 이하입니다");
            return null;
        }
        
        if (versionResponse.length < versionLength + 5) {
            displayMessage("Invalid version response: 응답 길이가 부족합니다 (필요: " + (versionLength + 5) + ", 실제: " + versionResponse.length + ")");
            return null;
        }
        
        // 버전 정보 추출 (5번째 바이트 부터 versionLength 만큼)
        byte[] versionInfo = new byte[versionLength];
        System.arraycopy(versionResponse, 5, versionInfo, 0, versionLength);

        displayMessage("VERSION [" + versionLength + "] = " + bytesToHex(versionInfo));
        displayMessage("==========================================");

        return versionInfo;
    } catch (Exception e) {
        displayMessage("버전 에러 : " + e.getMessage());
        return null;
    }
}

    /**
     *  LEA-128-ECB 암호화
     * @param key 16바이트 키
     * @param data 암호화할 데이터
     * @return 암호화된 데이터, 오류시 null
     */
    public byte[] encryptLea128Ecb(byte[] key, byte[] data) {
        if (serial == null) {
            Log.e(TAG, "CryptoSerial is not initialized");
            displayMessage("CryptoSerial is not initialized");
            return null;
        }

        try {
            displayMessage("LEA-128-ECB");

//            LEA-128-ECB
//            RQ [24] = D43424001000112233445566778899AABBCCDDEEFF23A4D8
//            RS [8] = D4B4240000F48FD8
//            RQ [11] = D4392D0003454342A2BCD8
//            RS [8] = D4B92D00006598D8
//            RQ [24] = D43A2D0010800000000000000000000000000000008890D8
//            RS [24] = D4BA2D0010C92ED5DDB2448EC936CA33088D204032CBE4D8
//            LEA-128-ECB encryption [16] = C92ED5DDB2448EC936CA33088D204032

            // 1단계: 키 설정 요청
            byte[] keyRequest = new byte[24]; // 헤더(2) + 길이(1) + 키(16) + 체크섬(4)
            keyRequest[0] = (byte)0xD4;  // 헤더
            keyRequest[1] = (byte)0x34;  // 키 설정 명령
            keyRequest[2] = (byte)0x24;  // 길이 상위 바이트
            keyRequest[3] = (byte)0x00; // 길이 하위 바이트
            keyRequest[4] = (byte)0x10;
            // 키 요청의 키 값을 복사진행.
            System.arraycopy(key, 0, keyRequest, 5, 16);  // 키 복사

            // 체크섬 계산 (실제 구현에서는 적절한 체크섬 계산 필요)
            keyRequest[21] = (byte)0x23;
            keyRequest[22] = (byte)0xA4;
            keyRequest[23] = (byte)0xD8;

            // 키 설정 요청 전송 및 응답 수신
            displayMessage("RQ [24] = " + bytesToHex(keyRequest));
            byte[] keyResponse = serial.sendRequest(keyRequest);

            if (keyResponse != null) {
                displayMessage("RS [" + keyResponse.length + "] = " + bytesToHex(keyResponse));
            }
            else {
                displayMessage("키 설정 응답 없음");
                return null;
            }


            // RQ [11] = D4392D0003454342A2BCD8
            // 2단계: 암호화 명령 설정 요청
            byte[] cmdRequest = new byte[11]; // 헤더(2) + 길이(2) + 명령(1) + 파라미터(3) + 체크섬(3)
            cmdRequest[0] = (byte)0xD4;  // 헤더
            cmdRequest[1] = (byte)0x39;  // 명령 설정
            cmdRequest[2] = (byte)0x2D;  // 길이
            cmdRequest[3] = (byte)0x00; // 길이
            cmdRequest[4] = CMD_LEA_128_ECB_ENCRYPT; // 암호화 명령
            cmdRequest[5] = (byte)0x45;  // 추가 파라미터 (로그에서 확인)
            cmdRequest[6] = (byte)0x43;  // 추가 파라미터 (로그에서 확인)
            cmdRequest[7] = (byte)0x42;  // 추가 파라미터 (로그에서 확인)
            // 체크섬 계산 (실제 구현에서는 적절한 체크섬 계산 필요)
            cmdRequest[8] = (byte)0xA2;
            cmdRequest[9] = (byte)0xBC;
            cmdRequest[10] = (byte)0xD8;

            // 암호화 명령 설정 요청 전송 및 응답 수신
            displayMessage("RQ [11] = " + bytesToHex(cmdRequest));
            byte[] cmdResponse = serial.sendRequest(cmdRequest);

            if (cmdResponse != null) {
                displayMessage("RS [" + cmdResponse.length + "] = " + bytesToHex(cmdResponse));
            }
            else {
                displayMessage("명령 설정 응답 없음");
                return null;
            }

            // RQ [24] = 800000000000000000000000000000008890D8

            // 3단계: 데이터 암호화 요청
            byte[] dataRequest = new byte[24]; // 헤더(2) + 길이(1) + 데이터(16) + 체크섬(5)
            dataRequest[0] = (byte)0xD4;  // 헤더
            dataRequest[1] = (byte)0x3A;  // 데이터 암호화 명령
            dataRequest[2] = (byte)0x2D;  // 데이터 길이 (16바이트)
            dataRequest[3] = (byte)0x00;
            dataRequest[4] = (byte)0x10;
            // data 배열의 0번 부터 뒤에 인자의 5번째부터 16바이트 복사 하겠다는 의미임.
            // data 0부터 16바이트를 5번째 부터 복사하겠단느 뜻.
            System.arraycopy(data, 0, dataRequest, 5, 16);  // 데이터 복사

            // 체크섬 계산 (실제 구현에서는 적절한 체크섬 계산 필요)
            dataRequest[21] = (byte)0x88;
            dataRequest[22] = (byte)0x90;
            dataRequest[23] = (byte)0xD8;

            // 데이터 암호화 요청 전송 및 응답 수신
            displayMessage("RQ [24] = " + bytesToHex(dataRequest));
            byte[] dataResponse = serial.sendRequest(dataRequest);

            if (dataResponse == null) {
                displayMessage("암호화 응답 없음");
                return null;
            }

            displayMessage("RS [" + dataResponse.length + "] = " + bytesToHex(dataResponse));

            // 암호화 결과 추출 (헤더 3바이트 제외)
            byte[] result = new byte[16];  // 암호화 결과는 16바이트
            System.arraycopy(dataResponse, 5, result, 0, 16);

            // 결과 로그 출력
            displayMessage("LEA-128-ECB encryption [16] = " + bytesToHex(result));

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Encryption error: " + e.getMessage(), e);
            displayMessage("Encryption error: " + e.getMessage());
            return null;
        }
    }

    /**
     *  Random 값 가져오기
     * @param length 생설한 난수의 바이트 길이
     * @return 생성된 난수, 오류시 null
     */
    public byte[] generateRandom(int length) {
        if (serial == null) {
            displayMessage("CryptoSerial 초기화 안됨");
            return null;
        }
        try {
            displayMessage("Start Random Generation");

            // 난수 생성 요청 패킷 구성
            byte[] request = new byte[8];
            // 일단 임시로.
            request[0] = (byte)0xD4; //헤더

            // 요청 로그 출력
            displayMessage("RQ [8] = " + bytesToHex(request));

            // 요청 전송 및 응답 수신
            byte[] response = serial.sendRequest(request);

            // 응답 검증
            if (response == null){
                displayMessage("난수 생성 실패 : 응답이 null 입니다.");
                return null;
            }

            // 응답 로그 출력
            displayMessage("RS [" + response.length + "] = " + bytesToHex(response));

            // 난수 데이터 추출 해서 return randomData 해주면 될듯.

        } catch (Exception e) {

        }

        return null;
    }

    /**
     * HMAC 계산
     * @param key MAC 계산에 사용할 키
     * @param data MAC을 계산할 데이터
     * @return 계산된 MAC 값, 오류시 null
     */
    public byte[] calculateHmac(byte[] key, byte[] data) {
        if (serial == null) {
            displayMessage("암호 모듈 초기화 안됨.");
            return null;
        }
        try {
            displayMessage("====================");
            displayMessage("Start HMAC Calculation");
            // 여기서 key는 PSK 를 불러온다음 사용해야댐, 하드웨어 모듈에 저장되어 있는.
            displayMessage("Key: " + bytesToHex(key));
            displayMessage("Data: " + bytesToHex(data));
        }
        catch (Exception e) {

        }
        // 임시로 널이라고한거임
        return null;
    }

    /**
     * PSK 저장
//     * @param psk 저장할 psk 데이터
     * @return true, 오류 발생 시 null
     */
    // byte[] psk
    public boolean setpskData(byte[] psk) {
        if (serial == null) {
            displayMessage("암호 모듈 초기화 오류");
            return false;
        }
        // psk 000102030405060708090a0b0c0d0e0f 라고 가정

        // psk 306E538A29ADAB6E8FDD92C02101E9C2
        try {
            displayMessage("PSK Saving to qSIM");

            // psk 자리수를 그냥 16바이트로 고정하고 진행.
            // 1단계: 키 설정 요청

            // 000102030405060708090A0B0C0D0E0F0
            byte[] pskSaveRequest = new byte[265];
            pskSaveRequest[0] = (byte) 0xD4;
            pskSaveRequest[1] = (byte) 0x19;
            pskSaveRequest[2] = (byte) 0x00;
            pskSaveRequest[3] = (byte) 0x01;
            pskSaveRequest[4] = (byte) 0x01;
            pskSaveRequest[5] = (byte) 0x01;

             // 키 요청의 키 값을 복사진행.
            System.arraycopy(psk, 0, pskSaveRequest, 6, 32);  // 키 복사

            // [22] 부터 [261] 까지 전체 0
            for(int i = 38; i <= 261; i++) {
                pskSaveRequest[i] = (byte) 0x00;
            }

            pskSaveRequest[262] = (byte) 0x55;
            pskSaveRequest[263] = (byte) 0xCF;
            pskSaveRequest[264] = (byte) 0xD8;

            displayMessage("RQ [265] = " + bytesToHex(pskSaveRequest));




            byte[] pskSaveResponse = serial.sendRequest(pskSaveRequest);

            if (pskSaveResponse != null) {
                displayMessage("RS [" + pskSaveResponse.length + "] = " + bytesToHex(pskSaveResponse));
                displayMessage("PSK 저장 성공");
            } else {
                displayMessage("PSK 저장 응답 없음");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "PSK save error: " + e.getMessage(), e);
            displayMessage("PSK save error: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * PSK 불러오기
     * @return psk 데이터, 오류 발생 시 null
     */
    public byte[] getpskData() {
        if (serial == null) {
            displayMessage("암호 모듈 초기화 오류");
            return null;
        }
        try {

            byte[] pskbringRequest = new byte[9];
            pskbringRequest[0] = (byte) 0xD4;
            pskbringRequest[1] = (byte) 0x1A;
            pskbringRequest[2] = (byte) 0x00;
            pskbringRequest[3] = (byte) 0x00;
            pskbringRequest[4] = (byte) 0x01;
            pskbringRequest[5] = (byte) 0x01;
            pskbringRequest[6] = (byte) 0xCE;
            pskbringRequest[7] = (byte) 0x3F;
            pskbringRequest[8] = (byte) 0xD8;

            displayMessage("RQ [9] = " + bytesToHex(pskbringRequest));

            byte[] pskbringResponse = serial.sendRequest(pskbringRequest);

            if (pskbringResponse != null) {
                displayMessage("RS [" + pskbringResponse.length + "] = " + bytesToHex(pskbringResponse));
                displayMessage("PSK Get Success");
            } else {
                displayMessage("PSK Get Response is null");
                return null;
            }
            // PSK 임의로 고정적으로 그냥 00112233445566778899AABBCCDDEEFF
            // 이후에 PSK만 추출해내야함.
            // PSK 추출 로직.


            int startIndex = 5;
            int endIndex = pskbringResponse.length - 3;

            // PSK 데이터 길이를 먼저 추출하고 얼만큼 담을건지 정해야 하니까
            int pskCount = 0;

            for (int i = startIndex; i < endIndex; i++) {
                if (pskbringResponse[i] != 0x00)
                    pskCount++;
            }

            // PSK 데이터 담을 바이트 배열 변수 선언
            byte[] pskData = new byte[pskCount];
            int destIndex = 0;

            for (int i = startIndex; i < endIndex; i++) {
                if(pskbringResponse[i] != 0x00) {
                    pskData[destIndex++] = pskbringResponse[i];
                }
            }

            displayMessage("Extracted PSK [" + pskCount + "] = " + bytesToHex(pskData));
            displayMessage("PSK Get Success");

            return pskData;

        } catch (Exception e) {
            Log.e(TAG, "PSK save error: " + e.getMessage(), e);
            displayMessage("PSK save error: " + e.getMessage());
            return null;
        }

    }

    /**
     *  리소스 해제
     */
    public void close() {
        if (serial != null) {
            serial.close();
        }
        isLoggedIn = false;
    }

    /**
     * 16진수 문자열을 바이트 배열로 변환
     */
    public static byte[] hexStringToBytes(String hexString) {
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

    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}