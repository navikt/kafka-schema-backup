package no.nav.nada

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.config.ApplicationConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun dataSourceFrom(config: DatabaseConfig, role: String = "user"): DataSource {
    return HikariDataSource(hikariConfigFrom(config))
}

fun databaseConfigFrom(appConfig: ApplicationConfig) = DatabaseConfig(
        host = appConfig.propertyOrNull("database.host")?.getString() ?: "localhost1",
        port = appConfig.propertyOrNull("database.port")?.getString()?.toInt() ?: 54321,
        name = appConfig.propertyOrNull("database.name")?.getString() ?: "nada-schema-backup",
        username = appConfig.propertyOrNull("database.user")?.getString() ?: "nada",
        password = appConfig.propertyOrNull("database.password")?.getString() ?: "nadapassword",
        local = appConfig.propertyOrNull("ktor.environment")?.getString() == "local"
)

private fun hikariConfigFrom(config: DatabaseConfig) =
        HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.name}"
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
            username = config.username
            password = config.password
        }

data class DatabaseConfig(
        val host: String,
        val port: Int,
        val name: String,
        val username: String,
        val password: String,
        val local: Boolean = false
)

internal fun migrate(dataSource: HikariDataSource, initSql: String = ""): Int =
        Flyway.configure().dataSource(dataSource).initSql(initSql).load().migrate()

internal fun clean(dataSource: HikariDataSource) = Flyway.configure().dataSource(dataSource).load().clean()

val ApplicationConfig.envKind get() = property("ktor.environment").getString()
