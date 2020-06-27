package no.nav.nada

import io.prometheus.client.Histogram
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.postgresql.util.PGobject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.sql.DataSource

class SchemaRepository(val dataSource: DataSource) {
    companion object {
        val QUERY_TIMER = Histogram.build()
                .name("query_timer")
                .namespace("no_nav_nada")
                .help("Time taken to perform db query")
                .labelNames("query")
                .register()
        val logger: Logger = LoggerFactory.getLogger(SchemaRepository::class.java)
    }

    fun saveSchema(key: SchemaRegistryKey, messageValue: SchemaRegistryMessage, timestamp: Long): Int {
        val timer = QUERY_TIMER.labels("save_schema").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                    queryOf(
                            """
                                INSERT INTO kafka_schema 
                                (id, subject, version, registry_id, schema_data, deleted, created)
                                VALUES (:id, :subject, :version, :registry_id, :schema_data, :deleted, :created)
                            """.trimIndent()
                            , mapOf(
                            "id" to ulid.nextULID(),
                            "registry_id" to messageValue.id,
                            "subject" to messageValue.subject,
                            "schema_data" to PGobject().apply {
                                type = "jsonb"
                                value = messageValue.schema.toString()
                            },
                            "version" to messageValue.version,
                            "deleted" to messageValue.deleted,
                            "created" to Instant.ofEpochMilli(timestamp)
                    )
                    ).asUpdate
            )
        }.also {
            logger.info("Saved $key to database")
            timer.observeDuration()
        }
    }

    fun topic(key: String): String {
        return key
    }
}