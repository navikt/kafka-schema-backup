package no.nav.nada

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant

class SchemaRepositoryTest {

    @Test
    fun `should be able to save a single schema`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val schema = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(schema)
            val saved = repo.findById(schema.id)!!
            assertThat(saved.registry_id).isEqualTo(schema.registry_id)
            assertThat(saved.id).isEqualTo(schema.id)
            assertThat(saved.version).isEqualTo(schema.version)
            assertThat(saved.subject).isEqualTo(schema.subject)
            assertThat(saved.schema).isEqualTo(schema.schema)
            assertThat(saved.deleted).isEqualTo(schema.deleted)
        }
    }
    @Test
    fun `should update old version with new version`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val schema = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(schema)
            val newSchema = schema.copy(id = ulid.nextULID(), version = 2)
            repo.save(newSchema)
            val oldSchema = repo.findById(schema.id)
            assertNotNull(oldSchema)
            assertThat(oldSchema!!.supersededBy).isEqualTo(newSchema.id)
        }
    }
}

internal object PostgresContainer {
    val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:12.3").apply {
            start()
        }
    }
}

internal object DataSource {
    val instance: HikariDataSource by lazy {
        HikariDataSource().apply {
            username = PostgresContainer.instance.username
            password = PostgresContainer.instance.password
            jdbcUrl = PostgresContainer.instance.jdbcUrl
            connectionTimeout = 1000L
        }
    }
}

internal fun withCleanDb(test: () -> Unit) = DataSource.instance.also { clean(it) }.run { test() }

internal fun withMigratedDb(test: () -> Unit) =
        DataSource.instance.also { clean(it) }.also { migrate(it) }.run { test() }
