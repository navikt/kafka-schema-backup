val kotlinVersion = "1.6.10"
val assertJVersion = "3.21.0"
val coroutinesVersion = "1.5.2"
val flywayVersion = "8.2.3"
val hikariVersion = "5.0.0"
val kafkaVersion = "3.0.0"
val junitJupiterVersion = "5.8.2"
val kotliqueryVersion = "1.6.1"
val ktorVersion = "1.6.7"
val log4jVersion = "2.17.1"
val micrometerVersion = "1.8.1"
val postgresVersion = "42.3.1"
val postgresTestcontainerVersion = "1.16.2"
val prometheusVersion = "0.14.1"
val serializerVersion = "0.20.0"
val slf4jVersion = "1.7.32"
val vaultJdbcVersion = "1.3.9"
val kotlinxHtmlJvmVersion = "0.7.3"
val kotlinxSerializationVersion = "1.3.1"
val kotlinxSerializationRuntimeVersion = "1.0-M1-1.4.0-rc"
val fatJarVersion = "1.0.5"
val sulkyVersion = "8.3.0"

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("com.dorkbox.GradleUtils") version "2.14"
    id("org.jmailen.kotlinter") version "3.16.0"
    id("com.github.ben-manes.versions") version "0.39.0"
}

repositories {
    mavenCentral()
}


dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializerVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializerVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationRuntimeVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinxHtmlJvmVersion")
    implementation("io.prometheus:simpleclient:$prometheusVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    implementation("com.vlkan.log4j2:log4j2-logstash-layout-fatjar:$fatJarVersion")
    implementation("de.huxhorn.sulky:de.huxhorn.sulky.ulid:$sulkyVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.testcontainers:postgresql:$postgresTestcontainerVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "15"
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")

    manifest {
        attributes["Main-Class"] = "no.nav.nada.AppKt"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }

    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
