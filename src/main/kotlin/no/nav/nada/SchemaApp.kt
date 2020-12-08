package no.nav.nada

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.config.ApplicationConfig
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.flywaydb.core.Flyway
import org.slf4j.event.Level
import javax.sql.DataSource

fun Application.schemaApi(
        appConfig: ApplicationConfig = this.environment.config,
        dataSource: DataSource = dataSourceFrom(databaseConfigFrom(appConfig)),
        schemaRepository: SchemaRepository = SchemaRepository(dataSource)
) {
    val jsonConfig = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true))
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
        json(
                json = jsonConfig,
                contentType = ContentType.Application.Json
        )
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
        nais()
        get("/") {
            val topics = schemaRepository.findTopics()
            call.respond(mapOf("topics" to topics))
        }
        get("/topic/{topic}") {
            call.parameters["topic"]?.let { topic ->
                val topicInfo = schemaRepository.topicInfo(topic)
                call.respond(mapOf("schemas" to topicInfo))
            } ?: call.respond(HttpStatusCode.BadRequest)
        }
        schemaRegistry(schemaRepository)
    }
}

fun serviceUser(appConfig: ApplicationConfig): ServiceUser {
    return ServiceUser(
            username = appConfig.property("serviceuser.username").getString(),
            password = appConfig.property("serviceuser.password").getString()
    )

}