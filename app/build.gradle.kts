plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val rustJniLibs = layout.buildDirectory.dir("rustJniLibs")
val signingKeystore = System.getenv("OSMIUM_KEYSTORE") ?: System.getenv("HELIUM_KEYSTORE")
val signingStorePassword = System.getenv("OSMIUM_STORE_PASSWORD") ?: System.getenv("HELIUM_STORE_PASSWORD")
val signingKeyAlias = System.getenv("OSMIUM_KEY_ALIAS") ?: System.getenv("HELIUM_KEY_ALIAS")
val signingKeyPassword = System.getenv("OSMIUM_KEY_PASSWORD") ?: System.getenv("HELIUM_KEY_PASSWORD")

tasks.register<Exec>("buildRustCore") {
    val outputDirectory = rustJniLibs.get().asFile
    inputs.files(fileTree(rootProject.file("native")) {
        exclude("target/**")
    })
    environment(
        "CARGO_TARGET_DIR",
        layout.buildDirectory.dir("rustTarget").get().asFile.absolutePath,
    )
    workingDir(rootProject.file("native"))
    doFirst {
        outputDirectory.deleteRecursively()
    }
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-o", outputDirectory.absolutePath,
        "build", "--release",
        "--manifest-path", rootProject.file("native/Cargo.toml").absolutePath,
    )
}

tasks.named("preBuild").configure {
    dependsOn("buildRustCore")
}

android {
    sourceSets.getByName("main").jniLibs.directories.add(rustJniLibs.get().asFile.absolutePath)
    namespace = "com.sajadxdp.osmium"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        // Keep the installed-package identity stable so existing users can upgrade without losing app data.
        applicationId = "computer.helium.mobile"
        minSdk = 28
        targetSdk = 37
        versionCode = 10
        versionName = "0.5.0"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // Release signing: credentials come from the environment so no secrets
    // are stored in the repository. The existing ~/.config/helium-android/signing.env remains supported.
    signingConfigs {
        create("release") {
            if (signingKeystore != null) {
                storeFile = file(signingKeystore)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
        }
        getByName("release") {
            if (signingKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.mozilla.geckoview:geckoview:160.0.20261116130455")
}
