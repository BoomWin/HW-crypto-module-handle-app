plugins {
    alias(libs.plugins.android.application)
    // Kotlin 플러그인을 유지하되, Java 파일들도 지원할 수 있습니다
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.tcp_ip_client"
    compileSdk = 35



    defaultConfig {
        applicationId = "com.example.tcp_ip_client"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // vectorDrawables 설정 제거 안해도 됩니다
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    // Compose 관련 설정을 제거합니다
    buildFeatures {
        compose = false
        // XML 레이아웃을 사용하기 위한 설정 추가
        viewBinding = true
    }
    // composeOptions 블록 제거

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 필요한 의존성만 유지하고 Compose 관련 의존성을 제거합니다
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    // usb-for-android
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")

    // 다음 Compose 관련 의존성 제거
    // implementation(libs.androidx.lifecycle.runtime.ktx)
    // implementation(libs.androidx.activity.compose)
    // implementation(platform(libs.androidx.compose.bom))
    // implementation(libs.androidx.ui)
    // implementation(libs.androidx.ui.graphics)
    // implementation(libs.androidx.ui.tooling.preview)
    // implementation(libs.androidx.material3)

    // 이 의존성들은 유지
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 다음 Compose 테스트 관련 의존성 제거
    // androidTestImplementation(platform(libs.androidx.compose.bom))
    // androidTestImplementation(libs.androidx.ui.test.junit4)
    // debugImplementation(libs.androidx.ui.tooling)
    // debugImplementation(libs.androidx.ui.test.manifest)
}