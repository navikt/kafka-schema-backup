package no.nav.nada

import io.ktor.config.ApplicationConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.io.File
import java.util.*

fun kafkaConfigFrom(config: ApplicationConfig, serviceUser: ServiceUser? = null): Properties {
    return Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.propertyOrNull("kafka.brokers")?.getString() ?: "localhost:9092")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        put(ConsumerConfig.GROUP_ID_CONFIG, "nada-schema-backup")
        if (serviceUser != null) {
            putAll(credentials(config, serviceUser))
        }
    }
}

private fun credentials(config: ApplicationConfig, serviceUser: ServiceUser) : Properties {
    return Properties().apply {
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(SaslConfigs.SASL_JAAS_CONFIG, """org.apache.kafka.common.security.plain.PlainLoginModule required username="${serviceUser.username}}" password="${serviceUser.password}"; """)
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getTrustStore(config))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getTrustStorePassword(config))
    }
}

private fun getTrustStore(config: ApplicationConfig): String {
    val path = config.propertyOrNull("kafka.truststore_path")?.getString() ?: "/etc/ssl/certs/java/cacerts"
    return File(path).absolutePath
}

private fun getTrustStorePassword(config: ApplicationConfig): String {
    return config.propertyOrNull("kafka.truststore_password")?.getString() ?: "changeme"
}

data class ServiceUser(val username: String, val password: String)