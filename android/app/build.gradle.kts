plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
android {
 namespace = "com.ytdash.studio"
 compileSdk = 35
 defaultConfig { applicationId = "com.ytdash.studio"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "2.0.0" }
 buildFeatures { compose = true }
 composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.browser:browser:1.8.0")
}
