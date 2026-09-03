plugins {
    id("com.android.application")
}

android {
    namespace = "org.codirex"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.codirex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
	
	buildFeatures {
		viewBinding = true
		buildConfig = true
	}
	
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
			excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    
}