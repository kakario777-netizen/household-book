plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GitHub 자동 빌드 번호 = 앱 versionCode (앱 안 업데이트가 이 번호로 새 버전을 판단)
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val tagVersion = rootProject.file("VERSION").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

android {
    namespace = "com.kakario.householdbook"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kakario.householdbook"
        minSdk = 29
        targetSdk = 34
        versionCode = buildNumber
        versionName = (tagVersion ?: "1.0") + " (#$buildNumber)"
    }

    signingConfigs {
        create("release") {
            // 서명 키는 저장소에 두지 않음 — GitHub Secrets에서 빌드할 때만 임시 파일로 풀어서 사용
            val ks = System.getenv("HB_KEYSTORE_PATH")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("HB_KEYSTORE_PASSWORD")
                keyAlias = "householdbook"
                keyPassword = System.getenv("HB_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
