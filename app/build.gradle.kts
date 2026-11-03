plugins {
    id("com.android.application") version "8.13.2"
}

// CI signs with the release key from GitHub secrets; local builds without it fall back to the debug key.
val releaseKeystore: String? = System.getenv("BIRDY_KEYSTORE")

android {
    namespace = "dev.birdy.minimal"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.birdy.minimal"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1 // grows with every CI build, so updates install
        // "v1.2" tag builds become "1.2"; branch and local builds are "dev"
        versionName = System.getenv("GITHUB_REF_NAME")?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }?.removePrefix("v") ?: "dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("BIRDY_KEYSTORE_PASSWORD")
            keyAlias = "birdy"
            keyPassword = storePassword // PKCS12 keystores use one password for both
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Robolectric runs MainActivity on the JVM and needs the merged resources for that.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1") // tests only, nothing of it ships in the APK
}

// Full assertion messages in the CI log, not just the line number.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
