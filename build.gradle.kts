plugins {
    kotlin("jvm") version "1.3.72"
    kotlin("plugin.serialization") version "1.3.72"
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
}

val assertJVersion = "3.16.1"
val coroutinesVersion = "1.3.3"
val flywayVersion = "6.5.0"
val hikariVersion = "3.4.2"
val kafkaVersion = "2.5.0"
val junitJupiterVersion = "5.6.1"
val kotliqueryVersion = "1.3.0"
val ktorVersion = "1.3.2"
val log4jVersion = "2.13.1"
val micrometerVersion = "1.5.2"
val postgresVersion = "42.2.14"
val postgresTestcontainerVersion = "1.15.0"
val prometheusVersion = "0.9.0"
val serializerVersion = "0.20.0"
val slf4jVersion = "1.7.30"
val vaultJdbcVersion = "1.3.1"

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializerVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializerVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
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
    implementation("com.vlkan.log4j2:log4j2-logstash-layout-fatjar:0.19")
    implementation("de.huxhorn.sulky:de.huxhorn.sulky.ulid:8.2.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.testcontainers:postgresql:$postgresTestcontainerVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.serialization.UnstableDefault,io.ktor.util.KtorExperimentalAPI"
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
