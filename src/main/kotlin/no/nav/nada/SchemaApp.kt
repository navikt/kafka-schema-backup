package no.nav.nada

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.config.ApplicationConfig
import io.ktor.features.CallLogging
import javax.sql.DataSource
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.flywaydb.core.Flyway
import org.slf4j.event.Level
import java.nio.file.Files
import java.nio.file.Paths

fun Application.schemaApi(
        appConfig: ApplicationConfig = this.environment.config,
        dataSource: DataSource = dataSourceFrom(databaseConfigFrom(appConfig)),
        schemaRepository: SchemaRepository = SchemaRepository(dataSource)
) {
    val flywayDs = dataSourceFrom(databaseConfigFrom(appConfig), "admin")
    val flyway = Flyway.configure().dataSource(flywayDs).load()
    flyway.migrate()
    SchemaReader.apply {
        create(kafkaConfigFrom(appConfig, serviceUser(appConfig)), schemaRepository)
        run()
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            !call.request.path().startsWith("/internal")
        }
    }
    install(DefaultHeaders)
    install(ContentNegotiation) {
        json(json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true)),
                contentType = ContentType.Application.Json)
    }
    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(
                PrometheusConfig.DEFAULT,
                CollectorRegistry.defaultRegistry,
                Clock.SYSTEM
        )
        meterBinders = listOf(
                ClassLoaderMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                ProcessorMetrics(),
                JvmThreadMetrics()
        )
    }
    routing {
        get("/isalive") {
            call.respondText("UP")
        }
        get("/isready") {
            call.respondText("UP")
        }
        get("/prometheus") {
            val names = call.request.queryParameters.getAll("name")?.toSet() ?: emptySet()
            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004), HttpStatusCode.OK) {
                TextFormat.write004(this, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names))
            }
        }
    }
}

fun serviceUser(appConfig: ApplicationConfig): ServiceUser? {
    val serviceUserBase = Paths.get(appConfig.property("nais.serviceuser").getString())
    return if (Files.exists(serviceUserBase)) {
        ServiceUser(
                username = Files.readString(serviceUserBase.resolve("username")),
                password = Files.readString(serviceUserBase.resolve("password"))
        )
    } else {
        null
    }
}
