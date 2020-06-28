package no.nav.nada

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import kotlinx.serialization.json.json

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
                    message = json {
                        "error_code" to 40401
                        "message" to "Subject '$subject' not found"
                    }
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
                        json {
                            "subject" to it.subject
                            "version" to it.version
                            "id" to it.registry_id
                            "schema" to it.schema
                        }
                    )
                } ?: call.respond(
                    status = HttpStatusCode.NotFound,
                    message = json {
                        "error_code" to 40402
                        "message" to "Version $version not found"
                    }
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
                message = json {
                    "error_code" to 40403
                    "message" to "Schema $id not found"
                }
            )
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}
