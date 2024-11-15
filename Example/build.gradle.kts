plugins {
    kotlin("jvm")
}

group = "org.llmlitekt.example"
version = "0.0.1"

kotlin {
    jvmToolchain(17)
}
repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":library"))
}

tasks.test {
    useJUnitPlatform()
}