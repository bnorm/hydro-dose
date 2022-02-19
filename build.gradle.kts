import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    application
    id("nebula.release") version "15.3.0"
    id("com.squareup.sqldelight")
}

group = "com.bnorm.hydro.dose"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "1.6.7"
    val sqldelightVersion = "1.5.3"
    val pi4jVersion = "2.1.1"
    val slf4jVersion = "1.7.36"
    val log4jVersion = "2.17.0"

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")

    implementation("com.squareup.sqldelight:sqlite-driver:$sqldelightVersion")
    implementation("com.squareup.sqldelight:coroutines-extensions-jvm:$sqldelightVersion")

    implementation("com.pi4j:pi4j-core:$pi4jVersion")
    implementation("com.pi4j:pi4j-plugin-raspberrypi:$pi4jVersion")
    implementation("com.pi4j:pi4j-plugin-pigpio:$pi4jVersion")
    implementation("com.pi4j:pi4j-plugin-linuxfs:$pi4jVersion")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    runtimeOnly("org.apache.logging.log4j:log4j-core:$log4jVersion")

    val junitVersion = "5.8.2"

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

sourceSets {
    main { resources { srcDir("src/main/sqldelight") } }
}

sqldelight {
    database("Database") {
        packageName = "dev.bnorm.hydro.db"
        schemaOutputDirectory = file("src/main/sqldelight/databases")
        verifyMigrations = true
    }
}
