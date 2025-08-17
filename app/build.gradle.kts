plugins {
    id("com.google.gms.google-services") // Firebase 서비스 사용
    alias(libs.plugins.android.application) // 안드로이드 애플리케이션 플러그인
    alias(libs.plugins.kotlin.android) // Kotlin 안드로이드 플러그인
    alias(libs.plugins.kotlin.compose) // Compose 플러그인
    id("kotlin-parcelize")
    id("kotlin-kapt") // 추가

}

android {
    namespace = "com.example.aihealth"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aihealth"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {

    // AndroidX & Jetpack Core
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0") // appcompat-resources 포함
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.annotation:annotation:1.9.1")

    // UI - Views
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // 2.2.0-beta01 -> 안정 버전으로 변경 권장
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0") // 중복 선언 제거

    // UI - Jetpack Compose
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")

    // Firebase (BOM - Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx") // firebase-auth 포함
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.firebaseui:firebase-ui-storage:8.0.2")

    // Google Services
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // 유틸리티 (Utility)
    implementation("com.github.bumptech.glide:glide:4.15.1")      // 이미지 로딩
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("org.jsoup:jsoup:1.15.3")                       // HTML 파싱
    implementation("androidx.emoji2:emoji2:1.4.0")                 // 이모지
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")

    // 테스트 (Test)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Jetpack Compose 테스트
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("org.mindrot:jbcrypt:0.4")
}