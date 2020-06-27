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

    fun exists(subject: String, version: Long, registry_id: Long, deleted: Boolean): Boolean {
        val timer = QUERY_TIMER.labels("exists_by_version_subject_registryid_deleted").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                    queryOf(
                            """SELECT exists(
|                           SELECT 1 FROM kafka_schema WHERE 
|                           subject = :subject 
|                           AND registry_id = :registry_id 
|                           AND deleted = :deleted 
|                           AND version = :version)""".trimMargin()
                            , mapOf(
                            "subject" to subject,
                            "registry_id" to registry_id,
                            "version" to version,
                            "deleted" to deleted
                    )).map { it.boolean("exists") }.asSingle
            )
        }.also {
            timer.observeDuration()
        } ?: false
    }

    fun findSchemaForSubject(subject: String): List<KafkaSchema> {
        val timer = QUERY_TIMER.labels("find_schema").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                    queryOf(
                            """SELECT * FROM kafka_schema WHERE subject = :subject ORDER BY created DESC""", mapOf("subject" to subject)
                    ).map { row ->
                        KafkaSchema(
                                id = row.string("id"),
                                registry_id = row.long("registry_id"),
                                subject = row.string("subject"),
                                schema = row.string("schema_data"),
                                version = row.long("version"),
                                deleted = row.boolean("deleted"),
                                created = row.instant("created"),
                                supersededAt = row.instantOrNull("superseded_at"),
                                supersededBy = row.stringOrNull("superseded_by")
                        )
                    }.asList
            ).also {
                timer.observeDuration()
            }
        }
    }

    fun saveSchema(key: SchemaRegistryKey, messageValue: SchemaRegistryMessage, timestamp: Long) {
        if (!exists(subject = messageValue.subject,
                        version = messageValue.version,
                        registry_id = messageValue.id,
                        deleted = messageValue.deleted)
        ) {
            val timer = QUERY_TIMER.labels("save_schema").startTimer()
            val newId = ulid.nextULID()
            using(sessionOf(dataSource)) { session ->
                session.run(
                        queryOf(
                                """
                                INSERT INTO kafka_schema 
                                (id, subject, version, registry_id, schema_data, deleted, created)
                                VALUES (:id, :subject, :version, :registry_id, :schema_data, :deleted, :created)
                            """.trimIndent()
                                , mapOf(
                                "id" to newId,
                                "registry_id" to messageValue.id,
                                "subject" to messageValue.subject,
                                "schema_data" to PGobject().apply {
                                    type = "jsonb"
                                    value = messageValue.schema
                                },
                                "version" to messageValue.version,
                                "deleted" to messageValue.deleted,
                                "created" to Instant.ofEpochMilli(timestamp)
                        )
                        ).asUpdate
                )
            }.also {
                timer.observeDuration()
            }
            val previous = findSchemaForSubject(messageValue.subject).firstOrNull { !it.deleted && it.id != newId }
            if (previous != null) {
                supersede(previous.copy(supersededAt = Instant.ofEpochMilli(timestamp), supersededBy = newId))
            }
        }

    }

    fun supersede(schema: KafkaSchema) {
        val timer = QUERY_TIMER.labels("supersede_query").startTimer()
        using(sessionOf(dataSource)) { session ->
            session.run(
                    queryOf(
                            """UPDATE kafka_schema SET superseded_at = :superseded_at, superseded_by = :superseded_by WHERE id = :id""",
                            mapOf("id" to schema.id, "superseded_at" to schema.supersededAt, "superseded_by" to schema.supersededBy)
                    ).asUpdate
            ).also {
                timer.observeDuration()
            }
        }
    }
}

data class KafkaSchema(val id: String,
                       val registry_id: Long,
                       val subject: String,
                       val schema: String,
                       val version: Long,
                       val deleted: Boolean,
                       val created: Instant,
                       val supersededAt: Instant?,
                       val supersededBy: String?)