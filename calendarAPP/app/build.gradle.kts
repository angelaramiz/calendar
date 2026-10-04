import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.fintrack.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fintrack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 61
        versionName = "1.0.55"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("KEYSTORE_PATH", "../fintrack.jks"))
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = localProps.getProperty("KEY_ALIAS", "")
            keyPassword = localProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

// NOTA tren Compose: ML Kit + activity-compose 1.9.0 empujan runtime a 1.9.0
// y foundation a 1.7.0 sobre material3 1.2.1 (deriva preexistente desde v1.0.54,
// verificada: la app arranca y opera). Se intentó forzar todo a 1.6.8 y eso
// SÍ rompe el arranque (NoSuchMethodError startReplaceGroup: el código compilado
// con el plugin Compose de Kotlin 2.4.0 + activity 1.9.0 exige runtime ≥1.7.0).
// Queda pendiente (con aprobación del dueño): subir el tren completo
// (BOM 2024.10+, material3 1.3.x) para eliminar también el FATAL raro de ripple
// en prefetch Lazy. Si OCR falla en real con NoClassDefFound, revisar.

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt") {
        exclude(group = "androidx.browser", module = "browser")
    }
    implementation("io.github.jan-tennert.supabase:postgrest-kt")

    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Koin
    val koinVersion = "3.5.3"
    implementation("io.insert-koin:koin-android:$koinVersion")
    implementation("io.insert-koin:koin-androidx-compose:$koinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // C7 widget: Glance 1.1.1 ABORTADO — sube foundation a 1.7.0 y runtime/ui
    // a 1.9.0 sobre el BOM 2024.06 (material3 1.2.1) y eso crashea los
    // clickables en prefetch (PlatformRipple vs IndicationNodeFactory).
    // Subir el tren Compose está prohibido en esta oleada, así que el widget
    // es el clásico (RemoteViews, cero dependencias nuevas).
    // C9 OCR: ML Kit Text Recognition 16.0.1 on-device (modelo latino
    // empaquetado, sin red, sin KSP/Hilt/Room: AAR plano). minSdk 21 <= 26.
    // Compatible Kotlin 2.4.0 / AGP 8.7.3.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
