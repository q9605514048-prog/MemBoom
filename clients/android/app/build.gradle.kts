plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "local.accountvault.android"; compileSdk = 36
    defaultConfig { applicationId = "local.accountvault.android"; minSdk = 26; targetSdk = 35; versionCode = 11; versionName = "0.8.1" }
    buildTypes {
        create("qa") {
            initWith(getByName("debug"));applicationIdSuffix=".qa";versionNameSuffix="-qa";matchingFallbacks.add("debug")
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(kotlin("test"))
}
