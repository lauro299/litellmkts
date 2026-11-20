import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
}

group = "io.github.lauro299"
version = "0.0.4-beta01"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "io.litellmkts.onnx"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":litellmkts"))
                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }

        // Intermediate source set shared by JVM and Android.
        // Uses compileOnly so the ONNX API is available at compile time;
        // the runtime artifact is supplied by jvmMain / androidMain respectively.
        val jvmAndroidMain by creating {
            dependsOn(commonMain)
            dependencies {
                compileOnly(libs.onnxruntime)
            }
        }

        val jvmMain by getting {
            dependsOn(jvmAndroidMain)
            dependencies {
                implementation(libs.onnxruntime)
            }
        }

        val androidMain by getting {
            dependsOn(jvmAndroidMain)
            dependencies {
                implementation(libs.onnxruntime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.engine)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "litellmkts-onnx", version.toString())

    pom {
        name = "litellmkts-onnx"
        description = "ONNX Runtime local inference support for litellmkts."
        inceptionYear = "2024"
        url = "https://github.com/lauro299/litellmkts/"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "lauro299"
                name = "lau"
            }
        }
        scm {
            url = "https://github.com/lauro299/litellmkts/"
        }
    }
}