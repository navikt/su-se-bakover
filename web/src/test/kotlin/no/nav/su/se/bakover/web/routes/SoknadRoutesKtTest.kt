package no.nav.su.se.bakover.web.routes

import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.build
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.personopplysninger
import no.nav.su.meldinger.kafka.soknad.SøknadMelding.Companion.fromConsumerRecord
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.web.ComponentTest
import no.nav.su.se.bakover.web.EmbeddedKafka.Companion.kafkaConsumer
import no.nav.su.se.bakover.web.susebakover
import no.nav.su.se.bakover.withCorrelationId
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration.of
import java.time.temporal.ChronoUnit.MILLIS
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SoknadRoutesKtTest : ComponentTest() {

    private val stubAktørId = "12345"
    fun soknadJson(fnr: Fnr) = build(personopplysninger = personopplysninger(fnr = fnr.toString())).toJson()

    @Test
    fun `lagrer og henter søknad`() {
        val fnr = Fnr("01010100001")
        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(), jwkProvider = JwkProviderStub)
        }) {
            val createResponse = withCorrelationId(Post, søknadPath) {
                addHeader(Authorization, jwt)
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
            }.response

            val søknadId = JSONObject(createResponse.content).getJSONArray("stønadsperioder").getJSONObject(0).getJSONObject("søknad").getInt("id")

            withCorrelationId(Get, "$søknadPath/$søknadId") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                val søknadInnhold = JSONObject(response.content).getJSONObject("json")
                assertEquals(JSONObject(soknadJson(fnr)).toString(), JSONObject(søknadInnhold.toString()).toString())
            }
        }
    }

    @Test
    fun `produserer kafka hendelse når søknad lagres på sak`() {
        val fnr = Fnr("01010100002")
        val correlationId = "my random UUID or something"
        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(personOppslag = personoppslag()), jwkProvider = JwkProviderStub)
        }) {
            handleRequest(Post, søknadPath) {
                addHeader(Authorization, jwt)
                addHeader(ContentType, Json.toString())
                addHeader(XCorrelationId, correlationId)
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                val sakId = JSONObject(response.content).getInt("id")
                val søknadId = JSONObject(response.content).getJSONArray("stønadsperioder").getJSONObject(0).getJSONObject("søknad").getInt("id")
                val records = kafkaConsumer.poll(of(1000, MILLIS)).records(SØKNAD_TOPIC)

                val ourRecords = records.filter { r -> r.key() == "$sakId" }
                val first = fromConsumerRecord(ourRecords.first()) as NySøknad
                assertEquals(first.correlationId, correlationId)
                assertEquals(1, ourRecords.size)
                assertEquals(NySøknad(
                        correlationId = correlationId,
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
    fun `knytter søknad til sak og stønadsperiode ved innsending`() {
        val fnr = Fnr("01010100004")
        var sakNr: Int
        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Post, søknadPath) {
                addHeader(Authorization, jwt)
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                sakNr = JSONObject(response.content).getInt("id")
            }

            // /soknad henter en liste... FIXME: skulle hete /soknader
            withCorrelationId(Get, "$sakPath/$sakNr") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                assertTrue(JSONObject(response.content).getJSONArray("stønadsperioder").getJSONObject(0).getJSONObject("søknad").getJSONObject("json").similar(JSONObject(soknadJson(fnr))))
            }
        }
    }

    fun personoppslag() = object : PersonOppslag {
        override fun person(ident: Fnr): ClientResponse = TODO("not implemented")

        override fun aktørId(ident: Fnr): String = stubAktørId
    }
}
