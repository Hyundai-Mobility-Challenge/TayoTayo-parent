import java.nio.file.Files
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    id("com.google.dagger.hilt.android")
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
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "DEMO_ETA", providers.gradleProperty("demoEta").orElse("true").get()) }
        getByName("release") { buildConfigField("boolean", "DEMO_ETA", "false") }
    }
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
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
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

kapt { correctErrorTypes = true }

// Gradle's Windows test worker can misread non-ASCII classpath entries. Keep the
// user's project location; stage only affected runtime entries in a fresh temp dir.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        doFirst {
            val root = Files.createTempDirectory("tayotayo-test-classpath-").toFile()
            val staged = mutableMapOf<File, File>()
            fun stage(source: File): File {
                if (source.absolutePath.all { it.code < 128 } || !source.exists()) return source
                return staged.getOrPut(source) {
                    val target = root.resolve("entry-${staged.size}" + if (source.isFile) ".jar" else "")
                    if (source.isDirectory) project.copy { from(source); into(target) }
                    else Files.copy(source.toPath(), target.toPath())
                    target
                }
            }
            classpath = project.files(classpath.files.map(::stage))
            testClassesDirs = project.files(testClassesDirs.files.map(::stage))
        }
    }
}
