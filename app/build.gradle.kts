plugins {
  id("com.android.application")
}

android {
  namespace = "dev.pausepd"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.pausepd"
    minSdk = 29
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
