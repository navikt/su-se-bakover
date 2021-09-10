package no.nav.su.se.bakover.service.statistikk

import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import no.nav.su.se.bakover.common.objectMapper
import org.slf4j.LoggerFactory

internal object StatistikkSchemaValidator {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val sakSchema: JsonSchema = createSchema("/statistikk/sak_schema.json")
    private val behandlingSchema: JsonSchema = createSchema("/statistikk/behandling_schema.json")
    private val stønadSchema: JsonSchema = createSchema("/statistikk/stonad_schema.json")

    fun validerSak(json: String): Boolean = validate(json, sakSchema)
    fun validerBehandling(json: String): Boolean = validate(json, behandlingSchema)
    fun validerStønad(json: String): Boolean = validate(json, stønadSchema)

    private fun validate(json: String, schema: JsonSchema): Boolean {
        val errors = schema.validate(objectMapper.readTree(json))
        if (errors.isNotEmpty()) log.error("Validering mot json-skjema feilet: $errors")
        return errors.isEmpty()
    }

    private fun createSchema(resource: String) = this::class.java.getResourceAsStream(resource)!!
        .readAllBytes()
        .toString(Charsets.UTF_8)
        .toJsonSchema()

    private fun String.toJsonSchema() = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V4)
        .getSchema(objectMapper.readTree(this))
}
