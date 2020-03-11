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
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.SøknadMelding.Companion.fromConsumerRecord
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
    private val stubAktørId = "12345"

    @Test
    fun `lagrer og henter søknad`() {
        val token = jwtStub.createTokenFor()
        val fnr = Fødselsnummer("01010100001")
        stubPdl(fnr)
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Post, søknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                println(response)
                assertEquals(Created, response.status())
            }.response

            withCorrelationId(Get, "$søknadPath?${Fødselsnummer.identLabel}=$fnr") {
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
            val lagreSøknadResponse = withCorrelationId(Post, søknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
            }.response

            val sakId = JSONObject(lagreSøknadResponse.content).getInt("id")
            val søknadId = JSONObject(lagreSøknadResponse.content).getJSONArray("søknader").getJSONObject(0).getInt("id")
            val records = kafkaConsumer.poll(of(1000, MILLIS))
                    .filter { it.topic() == SØKNAD_TOPIC }
            assertFalse(records.isEmpty())

            val ourRecords = records.filter { r -> r.key() == "$sakId" }
            val first = fromConsumerRecord(ourRecords.first()) as NySøknad
            assertEquals(first.correlationId, DEFAULT_CALL_ID)
            assertEquals(1, ourRecords.size)
            assertEquals(NySøknad(
                    correlationId = DEFAULT_CALL_ID,
                    søknadId = "$søknadId",
                    søknad = soknadJson(fnr),
                    sakId = "$sakId",
                    aktørId = stubAktørId,
                    fnr = fnr.toString()
            ), first)
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
            withCorrelationId(Post, søknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
            }

            withCorrelationId(Get, "$søknadPath?${Fødselsnummer.identLabel}=$fnr") {
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
            withCorrelationId(Post, søknadPath) {
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
                        "aktoerId":"$stubAktørId"
                    }
                """.trimIndent()))
        )

    }

    private fun soknadJson(fnr: Fødselsnummer) = """
        {
            "personopplysninger": {
                "fnr": "$fnr",
                "fornavn": "kake",
                "mellomnavn": "kjeks",
                "etternavn": "mannen",
                "telefonnummer": "12345678",
                "gateadresse": "gaten",
                "postnummer": "0050",
                "poststed": "Oslo",
                "bruksenhet": "50",
                "bokommune": "Oslo",
                "flyktning": true,
                "borFastINorge": true,
                "statsborgerskap": "NOR"
            },
            "boforhold": {
                "delerBolig": true,
                "borSammenMed": [
                    "voksen",
                    "barn"
                ],
                "delerBoligMed": [
                    {
                        "fnr": "voksen1",
                        "navn": "voksen jensen"
                    },
                    {
                        "fnr": "voksen2",
                        "navn": "voksen hansen"
                    }
                ]
            },
            "utenlandsopphold": {
                "utenlandsopphold": true,
                "registrertePerioder": [
                    {
                        "utreisedato": "2020-03-10",
                        "innreisedato": "2020-03-10"
                    }
                ],
                "planlagteUtenlandsopphold": true,
                "planlagtePerioder": [
                    {
                        "utreisedato": "2020-03-10",
                        "innreisedato": "2020-03-10"
                    }
                ]
            },
            "oppholdstillatelse": {
                "harVarigOpphold": false,
                "utløpsdato": "2020-03-10",
                "søktOmForlengelse": true
            },
            "inntektPensjonFormue": {
                "framsattKravAnnenYtelse": true,
                "framsattKravAnnenYtelseBegrunnelse": "annen ytelse begrunnelse",
                "harInntekt": true,
                "inntektBeløp": 2500.0,
                "harPensjon": true,
                "pensjonsOrdning": [
                    {
                        "ordning": "KLP",
                        "beløp": 2000.0
                    },
                    {
                        "ordning": "SPK",
                        "beløp": 5000.0
                    }
                ],
                "sumInntektOgPensjon": 7000.0,
                "harFormueEiendom": true,
                "harFinansFormue": true,
                "formueBeløp": 1000.0,
                "harAnnenFormue": true,
                "annenFormue": [
                    {
                        "typeFormue": "juveler",
                        "skattetakst": 2000.0
                    }
                ]
            },
            "forNav": {
                "målform": "norsk",
                "søkerMøttPersonlig": true,
                "harFullmektigMøtt": false,
                "erPassSjekket": true,
                "forNAVMerknader": "intet å bemerke"
            }
        }
    """.trimIndent()
}

