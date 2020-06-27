package no.nav.nada

import io.prometheus.client.Histogram
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.postgresql.util.PGobject
import javax.sql.DataSource

class SchemaRepository(val dataSource: DataSource) {
    companion object {
        val QUERY_TIMER = Histogram.build()
                .name("query_timer")
                .namespace("no_nav_nada")
                .help("Time taken to perform db query")
                .labelNames("query")
                .register()
    }
    fun saveSchema(record: ConsumerRecord<String, String>): Int {
        val timer = QUERY_TIMER.labels("save_schema").startTimer()
        return using(sessionOf(dataSource)) { session ->
            session.run(
                    queryOf(
                            """
                                INSERT INTO kafka_schema (id, schema_key, schema_value) 
                                VALUES (:id, :schema_key, :schema_value, :created
                            """.trimIndent()
                            , mapOf("id" to ulid.nextULID(), "schema_key" to record.key(),
                            "schema_value" to PGobject().apply {
                                type = "jsonb"
                            }
                    )
                    ).asUpdate
            )
        }.also {
            timer.observeDuration()
        }
    }
}