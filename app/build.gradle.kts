import java.util.Properties

plugins {
  id("com.android.application")
}

val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

android {
  namespace = "dev.pausepd"
  compileSdk = 36

  signingConfigs {
    if (keystoreProps.getProperty("storeFile") != null) {
      create("release") {
        storeFile = file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.findByName("release")
    }
  }

  defaultConfig {
    applicationId = "dev.pausepd"
    minSdk = 29
    targetSdk = 36
    versionCode = 3
    versionName = "1.2"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
