package no.nav.nada

import io.prometheus.client.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.RetriableException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.CoroutineContext

object SchemaReader : CoroutineScope {
    private val logger = LoggerFactory.getLogger(SchemaReader::class.java)
    private val addedSchemas = Counter.build().name("addedSchema").help("shows the added schemas").register()
    private val deletedSchemas = Counter.build().name("deletedSchema").help("shows the deleted schemas").register()
    private lateinit var job: Job
    private lateinit var kafkaProps: Properties
    private lateinit var schemaRepo: SchemaRepository
    private val json = Json { isLenient = true }
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

                                val key = json.decodeFromString(SchemaRegistryKey.serializer(), r.key())
                                when (key.keytype) {
                                    "SCHEMA" -> {
                                        val message =
                                            json.decodeFromString(SchemaRegistryMessage.serializer(), r.value())
                                        schemaRepo.saveSchema(messageValue = message, timestamp = r.timestamp())
                                        logger.info("saved schema $message")
                                        addedSchemas.inc()
                                    }
                                    "DELETE_SUBJECT" -> {
                                        val deleteMessage =
                                            json.decodeFromString(SchemaRegistryDeleteMessage.serializer(), r.value())
                                        schemaRepo.deleteSubject(deleteMessage.subject)
                                        deletedSchemas.inc()
                                    }
                                    else -> {
                                        logger.info("Message has unknown subject")
                                    }
                                }

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
data class SchemaRegistryMessage(
    val subject: String,
    val version: Long,
    val id: Long,
    val schema: String,
    val deleted: Boolean
)

@Serializable
data class SchemaRegistryDeleteMessage(val subject: String, val version: Long)

@Serializable
data class SchemaRegistryKey(val subject: String, val version: Long? = null, val magic: Long, val keytype: String)
