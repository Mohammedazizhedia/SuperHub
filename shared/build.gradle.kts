// Built with BFB.AI
plugins { 
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    // إجبار Kotlin على استخدام Java 17
    jvmToolchain(17)
    
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-websockets:2.3.12")
        }
        androidMain.dependencies { 
            implementation("io.ktor:ktor-client-okhttp:2.3.12") 
        }
    }
}

android {
    namespace = "com.bfbai.superhub.shared"
    compileSdk = 34
    defaultConfig { 
        minSdk = 26 
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
