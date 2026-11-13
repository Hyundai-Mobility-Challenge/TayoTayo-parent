plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") version "4.5.0" apply false
}
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "com.hanhyo.shuttle.parent"
    compileSdk = 36
    defaultConfig {
        // Reuses the already-authorized Firebase Android registration for this local prototype.
        applicationId = "com.ssafy.tonghak.parent"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.2-invitations"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["main"].java.srcDir("../common/kotlin")
    sourceSets["main"].assets.srcDir("../common/web")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // FCM brings an older Fragment transitively; align with Activity Result permissions.
    implementation("androidx.fragment:fragment:1.8.6")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation("com.caverock:androidsvg-aar:1.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
