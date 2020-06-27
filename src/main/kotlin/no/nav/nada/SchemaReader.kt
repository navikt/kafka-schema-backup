package no.nav.nada

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.RetriableException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.CoroutineContext

object SchemaReader : CoroutineScope {
    val logger = LoggerFactory.getLogger(SchemaReader::class.java)
    lateinit var job: Job
    lateinit var kafkaProps: Properties
    lateinit var schemaRepo: SchemaRepository
    val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true))
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    fun cancel() {
        job.cancel()
    }

    fun isRunning(): Boolean {
        logger.trace("Asked if running")
        return job.isActive
    }

    fun create(kafkaProps: Properties, schemaRepo: SchemaRepository) {
        this.job = Job()
        this.kafkaProps = kafkaProps
        this.schemaRepo = schemaRepo
    }

    fun run() {
        launch {
            logger.info("Starter kafka consumer")
            KafkaConsumer<String, String>(kafkaProps).use { consumer ->
                consumer.subscribe(listOf("_schemas"))
                while (job.isActive) {
                    try {
                        val records = consumer.poll(Duration.of(100, ChronoUnit.MILLIS))
                        records.asSequence()
                                .filter { it.key() != null && it.value() != null }
                                .forEach { r ->
                                    val message = json.parse(SchemaRegistryMessage.serializer(), r.value())
                                    schemaRepo.saveSchema(messageValue = message, timestamp = r.timestamp())
                                }
                        consumer.commitSync(Duration.ofSeconds(2))
                    } catch (e: RetriableException) {
                        logger.warn("Something went wrong while polling _schemas", e)
                    }
                }
            }
        }
    }

}

@Serializable
data class SchemaRegistryMessage(val subject: String, val version: Long, val id: Long, val schema: String, val deleted: Boolean)

@Serializable
data class SchemaRegistryKey(val subject: String, val version: Long, val magic: Long, val keytype: String)