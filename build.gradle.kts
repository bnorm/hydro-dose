import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    application
    id("nebula.release") version "15.3.0"
}

group = "com.bnorm.hydro.dose"

repositories {
    mavenCentral()
    maven { setUrl("https://oss.sonatype.org/content/groups/public") }
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.0-alpha0")
    implementation("org.slf4j:slf4j-simple:2.0.0-alpha0")
    implementation("com.pi4j:pi4j-core:2.0-SNAPSHOT")
    implementation("com.pi4j:pi4j-plugin-raspberrypi:2.0-SNAPSHOT")
    implementation("com.pi4j:pi4j-plugin-pigpio:2.0-SNAPSHOT")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("MainKt")
}
