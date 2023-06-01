package no.nav.su.se.bakover.statistikk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.jsonNode

/**
 * Generell skjemavaliderer.
 * Kan flyttes til common dersom flere moduler har behov for dette.
 */
internal object SchemaValidator {

    fun validate(json: String, schema: JsonSchema): Either<Set<ValidationMessage>, String> {
        val validated: Set<ValidationMessage> = schema.validate(jsonNode(json))
        return if (validated.isEmpty()) json.right() else validated.left()
    }

    fun createSchema(resource: String): JsonSchema {
        return this::class.java.getResourceAsStream(resource)!!.readAllBytes().toString(Charsets.UTF_8).toJsonSchema()
    }

    private fun String.toJsonSchema(): JsonSchema {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(jsonNode(this))
    }
}
