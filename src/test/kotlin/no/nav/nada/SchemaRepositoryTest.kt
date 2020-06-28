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

    @Test
    fun `finds topic name from subject`() {
        val schema = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
        assertThat(schema.topic()).isEqualTo("test-topic")
    }

    @Test
    fun `lists unique topic names`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val schema = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(schema)
            val keySchema = schema.copy(id = ulid.nextULID(), subject = "test-topic-key", created = Instant.now())
            repo.save(keySchema)
            val newVersion = schema.copy(id = ulid.nextULID(), version = 2, created = Instant.now())
            repo.save(newVersion)
            val topicList = repo.findTopics()
            assertThat(topicList).hasSize(1)
            assertThat(topicList.get(0)).isEqualTo("test-topic")
        }
    }

    @Test
    fun `fetches all versions of schema for a topic`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val schema = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(schema)
            val keySchema = schema.copy(id = ulid.nextULID(), subject = "test-topic-key", created = Instant.now())
            repo.save(keySchema)
            val newVersion = schema.copy(id = ulid.nextULID(), version = 2, created = Instant.now())
            repo.save(newVersion)
            val topicInfo = repo.topicInfo("test-topic")
            assertThat(topicInfo).hasSize(3)
            val keyValueMap = topicInfo.groupBy { it.subject }
            assertThat(keyValueMap).hasSize(2)
            assertThat(keyValueMap).containsOnlyKeys("test-topic-value", "test-topic-key")
        }
    }

    @Test
    fun `can list versions of a subject`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val schema = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(schema)
            val secondVersion = schema.copy(id = ulid.nextULID(), version = 2)
            repo.save(secondVersion)
            val versions = repo.getVersions("test-topic-value")
            assertThat(versions).hasSize(2)
            assertThat(versions).containsExactly(1L, 2L)
        }
    }

    @Test
    fun `lists unique subject names`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val schema = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(schema)
            val secondVersion = schema.copy(id = ulid.nextULID(), version = 2)
            repo.save(secondVersion)
            val versions = repo.getSubjects()
            assertThat(versions).hasSize(1)
            assertThat(versions).containsExactly("test-topic-value")
        }
    }

    @Test
    fun `can fetch schema for a subject and version`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val firstVersion = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(firstVersion)
            val secondVersion = firstVersion.copy(
                id = ulid.nextULID(), version = 2,
                schema =
                    """{"name": "DiceRoll", "type": "record", "fields": [{"name": "dice", "type": {"type": "array", "items": "int"}}, {"name": "count", "type": "int"}], "namespace": "no.nav.kafkacodelab"}"""
            )
            repo.save(secondVersion)
            val firstSchema = repo.getSchema("test-topic-value", 1L)!!
            assertThat(firstSchema.schema).isEqualTo(firstVersion.schema)
            val secondSchema = repo.getSchema("test-topic-value", 2L)!!
            assertThat(secondSchema.schema).isEqualTo(secondVersion.schema)
        }
    }

    @Test
    fun `can fetch schema by registry id`() {
        withMigratedDb {
            val repo = SchemaRepository(DataSource.instance)
            val firstVersion = KafkaSchema(registry_id = 1, subject = "test-topic-value", version = 1, deleted = false, created = Instant.now(), schema = "{}")
            repo.save(firstVersion)
            val secondVersion = firstVersion.copy(
                id = ulid.nextULID(), registry_id = 2, version = 2,
                schema =
                    """{"name": "DiceRoll", "type": "record", "fields": [{"name": "dice", "type": {"type": "array", "items": "int"}}, {"name": "count", "type": "int"}], "namespace": "no.nav.kafkacodelab"}"""
            )
            repo.save(secondVersion)
            assertThat(repo.getSchemaByRegistryId(1)).isEqualTo(firstVersion.schema)
            assertThat(repo.getSchemaByRegistryId(2)).isEqualTo(secondVersion.schema)
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
