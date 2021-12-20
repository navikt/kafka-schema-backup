@file:UseSerializers(InstantSerializer::class)

package no.nav.nada

import io.prometheus.client.Histogram
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
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
        val QUERY_TIMER: Histogram = Histogram.build()
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
|                           AND version = :version)""".trimMargin(),
                    mapOf(
                        "subject" to subject,
                        "registry_id" to registry_id,
                        "version" to version,
                        "deleted" to deleted
                    )
                ).map { it.boolean("exists") }.asSingle
            )
        }.also {
            timer.observeDuration()
        } ?: false
    }

    fun Row.toSchema(): KafkaSchema =
        KafkaSchema(
            id = this.string("id"),
            registry_id = this.long("registry_id"),
            subject = this.string("subject"),
            schema = this.string("schema_data"),
            version = this.long("version"),
            deleted = this.boolean("deleted"),
            created = this.instant("created"),
            supersededAt = this.instantOrNull("superseded_at"),
            supersededBy = this.stringOrNull("superseded_by")
        )

    fun findSchemaForSubject(subject: String): List<KafkaSchema> {
        val timer = QUERY_TIMER.labels("find_schema").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """SELECT * FROM kafka_schema WHERE subject = :subject ORDER BY created DESC""",
                    mapOf("subject" to subject)
                ).map { row -> row.toSchema() }.asList
            ).also {
                timer.observeDuration()
            }
        }
    }

    fun saveSchema(messageValue: SchemaRegistryMessage, timestamp: Long) {
        if (!exists(
                subject = messageValue.subject,
                version = messageValue.version,
                registry_id = messageValue.id,
                deleted = messageValue.deleted
            )
        ) {
            val schema = KafkaSchema(
                registry_id = messageValue.id,
                subject = messageValue.subject,
                schema = messageValue.schema,
                version = messageValue.version,
                deleted = messageValue.deleted,
                created = Instant.ofEpochMilli(timestamp)
            )
            save(schema)
        }
    }

    fun supersede(schema: KafkaSchema) {
        val timer = QUERY_TIMER.labels("supersede_query").startTimer()
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """UPDATE kafka_schema SET superseded_at = :superseded_at, superseded_by = :superseded_by WHERE id = :id""",
                    mapOf(
                        "id" to schema.id,
                        "superseded_at" to schema.supersededAt,
                        "superseded_by" to schema.supersededBy
                    )
                ).asUpdate
            ).also {
                timer.observeDuration()
            }
        }
    }

    fun save(schema: KafkaSchema) {
        val timer = QUERY_TIMER.labels("save_schema").startTimer()
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                                INSERT INTO kafka_schema 
                                (id, subject, version, registry_id, schema_data, deleted, created, topic)
                                VALUES (:id, :subject, :version, :registry_id, :schema_data, :deleted, :created, :topic)
                    """.trimIndent(),
                    mapOf(
                        "id" to schema.id,
                        "registry_id" to schema.registry_id,
                        "subject" to schema.subject,
                        "schema_data" to PGobject().apply {
                            type = "jsonb"
                            value = schema.schema
                        },
                        "version" to schema.version,
                        "deleted" to schema.deleted,
                        "created" to schema.created,
                        "topic" to schema.topic()
                    )
                ).asUpdate
            )
        }.also {
            timer.observeDuration()
        }
        val previous = findSchemaForSubject(schema.subject).firstOrNull { !it.deleted && it.id != schema.id }
        if (previous != null) {
            supersede(previous.copy(supersededAt = schema.created, supersededBy = schema.id))
        }
    }

    fun findById(id: String): KafkaSchema? {
        val timer = QUERY_TIMER.labels("find_by_id").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """SELECT * FROM kafka_schema WHERE id = :id""", mapOf("id" to id)
                ).map { row -> row.toSchema() }.asSingle
            )
        }.also { timer.observeDuration() }
    }

    fun findTopics(): List<String> {
        val timer = QUERY_TIMER.labels("find_topics").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("""SELECT DISTINCT topic FROM kafka_schema""", emptyMap()).map { it.string("topic") }.asList
            )
        }.also { timer.observeDuration() }
    }

    fun topicInfo(topic: String): List<KafkaSchema> {
        val timer = QUERY_TIMER.labels("topic_info").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("""SELECT * FROM kafka_schema WHERE topic = :topic""", mapOf("topic" to topic))
                    .map { r -> r.toSchema() }
                    .asList
            )
        }.also { timer.observeDuration() }
    }

    fun getSubjects(): List<String> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """SELECT DISTINCT subject FROM kafka_schema WHERE NOT deleted ORDER BY subject DESC""",
                    emptyMap()
                ).map { it.string("subject") }.asList
            )
        }
    }

    fun getVersions(subject: String): List<Long> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """SELECT DISTINCT version 
|                                         FROM kafka_schema
|                                         WHERE subject = :subject 
|                                         AND NOT deleted 
|                                         ORDER BY version ASC""".trimMargin(),
                    mapOf("subject" to subject)
                ).map { it.long("version") }.asList
            )
        }
    }

    fun getSchema(subject: String, version: Long): KafkaSchema? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """SELECT * FROM kafka_schema WHERE subject = :subject AND version = :version AND NOT deleted""",
                    mapOf("subject" to subject, "version" to version)
                ).map { it.toSchema() }.asSingle
            )
        }
    }

    fun getSchemaByRegistryId(id: Long): String? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                        SELECT schema_data FROM kafka_schema WHERE registry_id = :id ORDER BY created LIMIT 1
                    """.trimIndent(),
                    mapOf("id" to id)
                ).map { it.string("schema_data") }.asSingle
            )
        }
    }

    fun deleteSubject(subject: String): Int {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """UPDATE kafka_schema SET deleted = true WHERE subject = :subject""", mapOf("subject" to subject)
                ).asUpdate
            )
        }
    }
}

// @Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class KafkaSchema(
    val id: String = ulid.nextULID(),
    val registry_id: Long,
    val subject: String,
    val schema: String,
    val version: Long,
    val deleted: Boolean,
    val created: Instant,
    val supersededAt: Instant? = null,
    val supersededBy: String? = null
) {
    fun topic(): String {
        if (subject.endsWith("-key")) {
            return subject.replace("-key", "")
        } else if (subject.endsWith("-value")) {
            return subject.replace("-value", "")
        } else {
            return subject
        }
    }
}
