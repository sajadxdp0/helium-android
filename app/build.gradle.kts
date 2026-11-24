plugins {
    id("com.android.application")
}

val rustJniLibs = layout.buildDirectory.dir("rustJniLibs")

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
    namespace = "computer.helium.mobile"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "computer.helium.mobile"
        minSdk = 28
        targetSdk = 37
        versionCode = 9
        versionName = "0.4.0"
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
    // are stored in the repository. See ~/.config/helium-android/signing.env.
    signingConfigs {
        create("release") {
            val keystore = System.getenv("HELIUM_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("HELIUM_STORE_PASSWORD")
                keyAlias = System.getenv("HELIUM_KEY_ALIAS")
                keyPassword = System.getenv("HELIUM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (System.getenv("HELIUM_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
    implementation("org.mozilla.geckoview:geckoview:160.0.20261116130455")
}
