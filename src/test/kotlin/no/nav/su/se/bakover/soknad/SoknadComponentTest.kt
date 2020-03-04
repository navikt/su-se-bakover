package no.nav.su.se.bakover.soknad

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import com.google.gson.JsonParser
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.MessageBuilder.Companion.fromConsumerRecord
import no.nav.su.meldinger.kafka.Topics.SOKNAD_TOPIC
import no.nav.su.meldinger.kafka.soknad.NySoknad
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.EmbeddedKafka.Companion.kafkaConsumer
import no.nav.su.se.bakover.sak.sakPath
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Duration.of
import java.time.temporal.ChronoUnit.MILLIS
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SoknadComponentTest : ComponentTest() {

    private val parser = JsonParser()
    private val stubAktoerId = "12345"

    @Test
    fun `lagrer og henter søknad`() {
        val token = jwtStub.createTokenFor()
        val fnr = Fødselsnummer("01010100001")
        stubPdl(fnr)
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Post, soknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                println(response)
                assertEquals(Created, response.status())
            }.response

            withCorrelationId(Get, "$soknadPath?${Fødselsnummer.identLabel}=$fnr") {
                addHeader(Authorization, "Bearer $token")
            }.apply {
                assertEquals(OK, response.status())
                val json = JSONObject(response.content)
                assertEquals(parser.parse(soknadJson(fnr)), parser.parse(json.getJSONObject("json").toString())) // Må bruke JsonParser fordi json-elementene kan komme i forskjellig rekkefølge
            }
        }
    }

    @Test
    fun `produserer kafka hendelse når søknad lagres på sak`() {
        val token = jwtStub.createTokenFor()
        val fnr = Fødselsnummer("01010100002")
        stubPdl(fnr)
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            val lagreSøknadResponse = withCorrelationId(Post, soknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
            }.response

            val sakId = JSONObject(lagreSøknadResponse.content).getInt("id")
            val søknadId = JSONObject(lagreSøknadResponse.content).getJSONArray("søknader").getJSONObject(0).getInt("id")
            val records = kafkaConsumer.poll(of(1000, MILLIS))
                    .filter { it.topic() == SOKNAD_TOPIC }
            assertFalse(records.isEmpty())

            val ourRecords = records.filter { r -> r.key() == "$sakId" }
            assertEquals(1, ourRecords.size)
            assertEquals(NySoknad(
                    soknadId = "$søknadId",
                    soknad = soknadJson(fnr),
                    sakId = "$sakId",
                    aktoerId = stubAktoerId,
                    fnr = fnr.toString()
            ), fromConsumerRecord(ourRecords.first()))
        }
    }

    @Test
    fun `lagrer og henter søknad på fnr`() {
        val token = jwtStub.createTokenFor()
        val fnr = Fødselsnummer("01010100003")
        stubPdl(fnr)
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Post, soknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
            }

            withCorrelationId(Get, "$soknadPath?${Fødselsnummer.identLabel}=$fnr") {
                addHeader(Authorization, "Bearer $token")
            }.apply {
                assertEquals(OK, response.status())
                val json = JSONObject(response.content)
                assertEquals(parser.parse(soknadJson(fnr)), parser.parse(json.getJSONObject("json").toString()))
            }
        }
    }

    @Test
    fun `knytter søknad til sak ved innsending`() {
        val token = jwtStub.createTokenFor()
        val fnr = Fødselsnummer("01010100004")
        var sakNr: Int
        stubPdl(fnr)
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Post, soknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                sakNr = JSONObject(response.content).getInt("id")
            }

            // /soknad henter en liste... FIXME: skulle hete /soknader
            withCorrelationId(Get, "$sakPath/$sakNr/soknad") {
                addHeader(Authorization, "Bearer $token")
            }.apply {
                assertEquals(OK, response.status())
                assertTrue(JSONArray(response.content).getJSONObject(0).getJSONObject("json").similar(JSONObject(soknadJson(fnr))))
            }
        }
    }

    fun stubPdl(testIdent: Fødselsnummer) {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/person"))
                .withHeader(Authorization, WireMock.equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                .withHeader(HttpHeaders.XCorrelationId, AnythingPattern())
                .withQueryParam("ident", WireMock.equalTo(testIdent.toString()))
                .willReturn(WireMock.okJson("""
                    {
                        "aktoerId":"$stubAktoerId"
                    }
                """.trimIndent()))
        )

    }

    private fun soknadJson(fnr: Fødselsnummer) = """
    {
      "personopplysninger": {
        "fnr": "$fnr",
        "fornavn": "fornavn",
        "mellomnavn": "ØÆÅ",
        "etternavn": "etternavn",
        "telefonnummer": "90011900",
        "gateadresse": "storgata 1",
        "bruksenhet": "20e",
        "postnummer": "0909",
        "poststed": "Oslo",
        "bokommune": "Oslo",
        "statsborgerskap": "Tunisisk",
        "flyktning": "true",
        "bofastnorge": "true"
      },
      "boforhold": {
        "borSammenMed": [
          "over18"
        ],
        "delerBoligMed": [
          {
            "navn": "Turid Schønberg",
            "fødselsnummer": "12312312312312"
          }
        ],
        "delerDuBolig": "true"
      },
      "utenlandsopphold": {
        "utenlandsoppholdArray": [
          {
            "utreisedato": "31122019",
            "innreisedato": "04012020"
          }
        ],
        "PlanlagtUtenlandsoppholdArray": [
          {
            "planlagtUtreisedato": "01032020",
            "planlagtInnreisedato": "05032020"
          },
          {
            "planlagtUtreisedato": "01042020",
            "planlagtInnreisedato": "05042020"
          }
        ],
        "utenlandsopphold": "true",
        "planlagtUtenlandsopphold": "true"
      },
      "oppholdstillatelse": {
        "varigopphold": "false",
        "oppholdstillatelseUtløpsdato": "30/10/2024",
        "soektforlengelse": "false"
      },
      "inntektPensjonFormue": {
        "pensjonsOrdning": [
          {
            "ordning": "KLP",
            "beløp": "99"
          },
          {
            "ordning": "SPK",
            "beløp": "98"
          }
        ],
        "kravannenytelse": "true",
        "kravannenytelseBegrunnelse": "Hundepensjon",
        "arbeidselleranneninntekt": "true",
        "arbeidselleranneninntektBegrunnelse": "2500",
        "hardupensjon": "true",
        "sumPersoninntekt": "30000",
        "harduformueeiendom": "true",
        "formueBeløp": "2323",
        "hardufinansformue": "false",
        "harduannenformueeiendom": "true",
        "typeFormue": "hytte i afrika",
        "samletSkattetakst": "3500",
        "sosialstonad": "true"
      },
      "forNAV": {
        "maalform": "nynorsk",
        "personligmote": "ja",
        "fullmektigmote": "ja",
        "passsjekk": "ja",
        "forNAVmerknader": "Trivelig type"
      }
    }
""".trimIndent()
}

