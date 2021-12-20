package no.nav.nada

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun Route.schemaRegistry(schemaRepository: SchemaRepository) {
    get("/subjects") {
        val subjects = schemaRepository.getSubjects()
        call.respond(subjects)
    }
    get("/subjects/{subject}/versions") {
        call.parameters["subject"]?.let { subject ->
            val versions = schemaRepository.getVersions(subject)
            if (versions.isEmpty()) {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = buildJsonObject(fun JsonObjectBuilder.() {
                        put("error_code", JsonPrimitive(40401))
                        put("message", JsonPrimitive("Subject '$subject' not found"))
                    })
                )
            } else {
                call.respond(versions)
            }
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
    get("/subjects/{subject}/versions/{version}") {
        call.parameters["subject"]?.let { subject ->
            call.parameters["version"]?.let { version ->
                schemaRepository.getSchema(subject, version.toLong())?.let {
                    call.respond(
                        buildJsonObject(fun JsonObjectBuilder.() {
                            put("subject", JsonPrimitive(it.subject))
                            put("version", JsonPrimitive(it.version))
                            put("id", JsonPrimitive(it.registry_id))
                            put("schema", JsonPrimitive(it.schema))
                        })
                    )
                } ?: call.respond(
                    status = HttpStatusCode.NotFound,
                    message = buildJsonObject(fun JsonObjectBuilder.() {
                        put("error_code", JsonPrimitive(40402))
                        put("message", JsonPrimitive("Version $version not found"))
                    })
                )
            }
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
    get("/schemas/ids/{id}") {
        call.parameters["id"]?.let { id ->
            schemaRepository.getSchemaByRegistryId(id.toLong())?.let {
                call.respond(mapOf("schema" to it))
            } ?: call.respond(
                status = HttpStatusCode.NotFound,
                message = buildJsonObject(fun JsonObjectBuilder.() {
                    put("error_code", JsonPrimitive(40403))
                    put("message", JsonPrimitive("Schema $id not found"))
                })
            )
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}