package no.nav.nada

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.ConsumerRecord
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

    fun run () {
        launch {
            logger.info("Starter kafka consumer")
            KafkaConsumer<String, String>(kafkaProps).use { consumer ->
                consumer.subscribe(listOf("__schemas"))
                while(job.isActive) {
                    try {
                        val records = consumer.poll(Duration.of(100, ChronoUnit.MILLIS))
                        records.asSequence().forEach {
                            schemaRepo.saveSchema(it)
                        }
                    } catch (e: RetriableException) {
                        logger.warn("Something went wrong while polling __schema", e)
                    }
                }
            }
        }
    }

}