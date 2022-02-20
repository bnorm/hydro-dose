import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.nebula.release)
    id("com.squareup.sqldelight")
}

group = "com.bnorm.hydro.dose"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.ktor)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")

    implementation(libs.bundles.sqldelight)

    implementation(libs.bundles.pi4j)

    implementation(libs.bundles.log4j.api)
    runtimeOnly(libs.bundles.log4j.runtime)

    testImplementation(libs.bundles.junit.api)
    testRuntimeOnly(libs.bundles.junit.runtime)
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
