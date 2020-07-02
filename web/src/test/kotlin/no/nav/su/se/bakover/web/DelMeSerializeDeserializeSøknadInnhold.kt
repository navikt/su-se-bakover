package no.nav.su.se.bakover.web

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.meldinger.kafka.soknad.Oppholdstillatelse
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import org.junit.jupiter.api.Test

class DelMeSerializeDeserializeSøknadInnhold {

    val enumMapperModule = SimpleModule().apply {
        this.addDeserializer(
            Oppholdstillatelse.OppholdstillatelseType::class.java,
            object : StdDeserializer<Oppholdstillatelse.OppholdstillatelseType>(Oppholdstillatelse.OppholdstillatelseType::class.java) {
                override fun deserialize(
                    p: JsonParser,
                    ctxt: DeserializationContext?
                ): Oppholdstillatelse.OppholdstillatelseType {
                    val node: JsonNode = p.codec.readTree(p)
                    return Oppholdstillatelse.OppholdstillatelseType.fromString(node.asText())!!
                }
            }
        )
    }

    val jsonMapper: ObjectMapper = JsonMapper.builder()
        .addModule(JavaTimeModule())
        .addModule(KotlinModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
        .addModule(enumMapperModule)
        .build()

    @Test
    fun serialize() {

        val build = SøknadInnholdTestdataBuilder.build()
        println(jsonMapper.writeValueAsString(build))
    }

    @Test
    fun deserialize() {
        //language=JSON
        val json = """
            {
                "personopplysninger":{
                    "aktørid":"123",
                    "fnr":"12345678910",
                    "fornavn":"fornavn",
                    "mellomnavn":null,
                    "etternavn":"etternavn",
                    "telefonnummer":"12345678",
                    "gateadresse":"gateadresse",
                    "postnummer":"0050",
                    "poststed":"Oslo",
                    "bruksenhet":"102",
                    "bokommune":"Oslo",
                    "statsborgerskap":"NOR"
                },
                "uførevedtak":{
                    "harUførevedtak":true
                },
                "flyktningsstatus":{
                    "registrertFlyktning":true
                },
                "oppholdstillatelse":{
                    "erNorskStatsborger":true,
                    "harOppholdstillatelse":null,
                    "oppholdstillatelseType":"midlertidig",
                    "oppholdstillatelseMindreEnnTreMåneder":null,
                    "oppholdstillatelseForlengelse":null,
                    "statsborgerskapAndreLand":false,
                    "statsborgerskapAndreLandFritekst":null
                },
                "boforhold":{
                    "borOgOppholderSegINorge":true,
                    "delerBoligMedVoksne":false,
                    "delerBoligMed":null,
                    "ektemakeEllerSamboerUnder67År":null,
                    "ektemakeEllerSamboerUførFlyktning":null
                },
                "utenlandsopphold":{
                    "registrertePerioder":[],
                    "planlagtePerioder":[]
                },
                "inntektOgPensjon":{
                    "forventetInntekt":null,
                    "tjenerPengerIUtlandetBeløp":null,
                    "andreYtelserINav":null,
                    "andreYtelserINavBeløp":null,
                    "søktAndreYtelserIkkeBehandletBegrunnelse":null,
                    "sosialstønadBeløp":null,
                    "trygdeytelserIUtlandetBeløp":null,
                    "trygdeytelserIUtlandet":null,
                    "trygdeytelserIUtlandetFra":null,
                    "pensjon":[]
                },
                "formue":{
                    "borIBolig":null,
                    "verdiPåBolig":null,
                    "boligBrukesTil":null,
                    "depositumsBeløp":null,
                    "kontonummer":null,
                    "verdiPåEiendom":null,
                    "eiendomBrukesTil":null,
                    "verdiPåKjøretøy":null,
                    "kjøretøyDeEier":null,
                    "innskuddsBeløp":null,
                    "verdipapirBeløp":null,
                    "skylderNoenMegPengerBeløp":null,
                    "kontanterBeløp":null
                },
                "forNav":{
                    "harFullmektigEllerVerge":null
                }
            }
        """.trimIndent()
        val readValue = jsonMapper.readValue<SøknadInnhold>(json)
        println(readValue)
    }
}
