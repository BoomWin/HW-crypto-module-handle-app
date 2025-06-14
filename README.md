# HW-crypto-module-handle-app

## 프로젝트 개요

본 프로그램은 논문을 작성하기 위해 개발되었습니다.
Android 기반의 하드웨어 암호모듈 제어 애플리케이션입니다. USB Serial 통신을 통해 QSIM 암호모듈과 연동하고, TCP/IP 네트워크 통신을 통한 보안 인증 프로토콜을 구현합니다.

## 주요 기능

### 1. 암호모듈 기능
- **LEA-128-ECB 암호화**: 16바이트 키를 사용한 LEA 알고리즘 암호화
- **HMAC 계산**: 메시지 인증 코드 생성 및 검증
- **난수 생성**: 하드웨어 기반 안전한 난수 생성
- **PSK 관리**: Pre-Shared Key 저장 및 관리
- **버전 정보 조회**: 암호모듈 펌웨어 버전 확인

### 2. 네트워크 통신
- **TCP/IP 클라이언트**: 서버와의 실시간 통신
- **인증 프로토콜**: Challenge-Response 기반 상호 인증
- **Nonce 교환**: 클라이언트/서버 간 논스 값 교환
- **메시지 암호화**: 통신 메시지 암호화 및 무결성 검증

### 3. USB Serial 통신
- **하드웨어 연결**: USB를 통한 암호모듈 연결
- **명령 전송**: 암호모듈 제어 명령 송수신
- **데이터 검증**: 체크섬 기반 데이터 무결성 검증
- **오류 처리**: 통신 오류 감지 및 재시도

## 프로젝트 구조

```
tcp_ip_client/
├── app/
│   ├── src/main/java/com/example/tcp_ip_client/
│   │   ├── MainActivity.java       # 메인 UI 및 전체 기능 관리
│   │   ├── TCPClient.java         # TCP/IP 클라이언트 통신
│   │   ├── CryptoModule.java      # 암호모듈 기능 제어
│   │   └── CryptoSerial.java      # USB Serial 통신
│   ├── src/main/res/
│   │   ├── layout/                # UI 레이아웃 파일들
│   │   ├── values/                # 문자열, 색상 등 리소스
│   │   └── mipmap-*/              # 앱 아이콘 리소스
│   └── src/main/AndroidManifest.xml
├── build.gradle.kts               # 앱 빌드 설정
├── settings.gradle.kts            # 프로젝트 설정
└── gradle.properties             # Gradle 속성
```

## 기술 스택

- **플랫폼**: Android (API 24+)
- **언어**: Java + Kotlin
- **빌드 도구**: Gradle
- **UI**: Android Views (XML Layout + ViewBinding)
- **암호화**: LEA-128-ECB, HMAC
- **통신**: TCP/IP Socket, USB Serial
- **의존성**:
  - `usb-serial-for-android`: USB Serial 통신
  - AndroidX 라이브러리들
  - Material Design Components

## 시스템 요구사항

- **Android 버전**: 7.0 (API 24) 이상
- **권한**: 
  - `INTERNET`: 네트워크 통신
  - `ACCESS_NETWORK_STATE`: 네트워크 상태 확인
  - `USB_FEATURE`: USB 호스트 모드
- **하드웨어**: USB OTG 지원 Android 디바이스
- **암호모듈**: QSIM 하드웨어 암호모듈

## 설치 및 실행

### 1. 개발 환경 설정
```bash
# 프로젝트 클론
git clone <repository-url>
cd HW-crypto-module-handle-app/tcp_ip_client

# Android Studio에서 프로젝트 열기
# 또는 Gradle 명령어 사용
./gradlew build
```

### 2. 앱 설치
```bash
# APK 빌드
./gradlew assembleDebug

# 디바이스에 설치
./gradlew installDebug
```

### 3. 하드웨어 연결
1. QSIM 암호모듈을 Android 디바이스에 USB로 연결
2. 앱에서 "Hardware Init" 버튼 클릭
3. USB 권한 허용 후 연결 완료

## 사용법

### 1. 암호모듈 초기화
1. USB 케이블로 QSIM 모듈 연결
2. "Hardware Init" 또는 "USB Connect" 버튼 클릭
3. "Get Version" 버튼으로 모듈 정보 확인

### 2. TCP/IP 서버 연결
1. 서버 IP 주소와 포트 번호 입력
2. "Connect" 버튼으로 서버 연결
3. "Disconnect" 버튼으로 연결 해제

### 3. 인증 프로토콜 실행
1. "Send Random" 버튼으로 클라이언트 논스 전송
2. 서버 응답 확인 후 "Calculate MAC" 버튼 클릭
3. HMAC 계산 및 인증 결과 확인

### 4. PSK 관리
1. "Save Data" 버튼으로 PSK 저장
2. "Bring Data" 버튼으로 저장된 PSK 조회

## 보안 기능

### 암호화 알고리즘
- **LEA (Lightweight Encryption Algorithm)**: 128비트 키, ECB 모드
- **HMAC**: SHA 기반 메시지 인증 코드
- **하드웨어 난수 생성**: TRNG 기반 안전한 난수

### 인증 프로토콜
1. **Challenge-Response**: 클라이언트/서버 상호 인증
2. **Nonce 교환**: 재전송 공격 방지
3. **PSK 기반 인증**: 사전 공유키를 이용한 인증
4. **메시지 무결성**: HMAC을 통한 데이터 무결성 검증

## 트러블슈팅

### USB 연결 문제
- USB OTG 케이블 사용 확인
- USB 디버깅 모드 활성화
- 앱에서 USB 권한 허용

### 네트워크 연결 문제
- 서버 IP/포트 정확성 확인
- 네트워크 연결 상태 확인
- 방화벽 설정 확인

### 암호모듈 통신 오류
- 하드웨어 연결 상태 확인
- 모듈 전원 공급 확인
- 시리얼 통신 속도 설정 확인

## 개발자 정보

### 주요 클래스 설명

#### MainActivity.java
- UI 이벤트 처리 및 전체 애플리케이션 로직 관리
- TCP/IP 및 USB Serial 연결 관리
- 인증 프로토콜 구현

#### TCPClient.java
- TCP/IP 소켓 통신 담당
- 서버와의 메시지 송수신
- 논스 값 관리 및 인증 데이터 처리

#### CryptoModule.java
- QSIM 암호모듈 제어
- 암호화/복호화 기능 제공
- HMAC 계산 및 PSK 관리

#### CryptoSerial.java
- USB Serial 통신 인터페이스
- 하드웨어 명령 송수신
- 체크섬 검증 및 오류 처리

## 라이선스

이 프로젝트는 연구 목적으로 개발되었습니다.
암호모듈 전체 제어 명령어를 인지 후 추가 업데이트 필요함.

## 버전 히스토리

- **v1.0**: 초기 버전
  - LEA-128-ECB 암호화 지원
  - TCP/IP 클라이언트 기능
  - USB Serial 통신
  - 기본 인증 프로토콜
