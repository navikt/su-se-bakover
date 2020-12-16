package no.nav.su.se.bakover.client.sts

import com.fasterxml.jackson.databind.JsonMappingException
import com.worldturner.medeia.api.UrlSchemaSource
import com.worldturner.medeia.api.ValidationFailedException
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi
import com.worldturner.medeia.schema.validation.SchemaValidator
import no.nav.su.se.bakover.client.statistikk.SakSchema
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.time.LocalDate

data class Person(
    val name: String
)

class ReadObjectExampleTest {
    private val api = MedeiaJacksonApi()

    fun parseValidExample() {
        val validator = loadSchema()
        val unvalidatedParser =
            objectMapper.factory.createParser(javaClass.getResource("/readobject/valid-person.json"))
        val validatedParser = api.decorateJsonParser(validator, unvalidatedParser)
        val person = objectMapper.readValue(validatedParser, Person::class.java)
        System.out.println(person.name)
    }

    fun parseInvalidExample() {
        val validator = loadSchema()
        val unvalidatedParser =
            objectMapper.factory.createParser(javaClass.getResource("/readobject/invalid-person.json"))
        val validatedParser = api.decorateJsonParser(validator, unvalidatedParser)
        try {
            objectMapper.readValue(validatedParser, Person::class.java)
            throw IllegalStateException("Invalid json data passed validation")
        } catch (e: JsonMappingException) {
            if (e.cause is ValidationFailedException) {
                // Expected
                println("Validation failed as expected: " + e.cause)
            } else {
                throw e
            }
        }
    }

    private fun loadSchema(): SchemaValidator {
        val source = UrlSchemaSource(
            javaClass.getResource("/readobject/person-address-schema.json")
        )
        return api.loadSchema(source)
    }

    @Test
    fun test() {
        val validator = api.loadSchema(UrlSchemaSource(this::class.java.getResource("/sak_schema.json")))
        val s = StringWriter()
        val unvalidatedGenerator = objectMapper.factory.createGenerator(s)
        val validatedGenerator = api.decorateJsonGenerator(validator, unvalidatedGenerator)

        val sakschema = SakSchema(
            funksjonellTid = Tidspunkt.now().toString(),
            tekniskTid = Tidspunkt.now().toString(),
            opprettetDato = LocalDate.now().toString(),
            sakId = "1",
            aktorId = 1,
            saksnummer = "1",
            ytelseType = "1",
            sakStatus = "1",
            avsender = "1",
            versjon = 1,
            aktorer = emptyList(),
            underType = null,
            ytelseTypeBeskrivelse = null,
            underTypeBeskrivelse = null,
            sakStatusBeskrivelse = null,
        )

        val a = objectMapper.writeValue(validatedGenerator, sakschema)
    }
}
