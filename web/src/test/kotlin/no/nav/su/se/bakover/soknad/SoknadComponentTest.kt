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
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.build
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.personopplysninger
import no.nav.su.meldinger.kafka.soknad.SøknadMelding.Companion.fromConsumerRecord
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.EmbeddedKafka.Companion.kafkaConsumer
import no.nav.su.se.bakover.sak.sakPath
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration.of
import java.time.temporal.ChronoUnit.MILLIS
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SoknadComponentTest : ComponentTest() {

    private val parser = JsonParser()
    private val stubAktørId = "12345"
    fun soknadJson(fnr: Fødselsnummer) = build(personopplysninger = personopplysninger(fnr = fnr.toString())).toJson()

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
                assertEquals(parser.parse(soknadJson(fnr)), parser.parse(json.getJSONObject("søknad").getJSONObject("json").toString())) // Må bruke JsonParser fordi json-elementene kan komme i forskjellig rekkefølge
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
            withCorrelationId(Post, søknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                val sakId = JSONObject(response.content).getInt("id")
                val søknadId = JSONObject(response.content).getJSONArray("stønadsperioder").getJSONObject(0).getJSONObject("søknad").getInt("id")
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
                assertTrue(JSONObject(response.content).getJSONObject("søknad").getJSONObject("json").similar(JSONObject(soknadJson(fnr))))
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
                assertTrue(JSONArray(response.content).getJSONObject(0).getJSONObject("søknad").getJSONObject("json").similar(JSONObject(soknadJson(fnr))))
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
}
