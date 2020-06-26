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
import kotlin.test.assertEquals
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.build
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.personopplysninger
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.client.stubs.SuKafkaClientStub
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.*
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SoknadRoutesKtTest {

    private val stubAktørId = "12345"
    fun soknadJson(fnr: Fnr) = build(personopplysninger = personopplysninger(fnr = fnr.toString())).toJson()

    @Test
    fun `lagrer og henter søknad`() {
        val fnr = Fnr("01010100001")
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val createResponse = defaultRequest(Post, søknadPath) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
            }.response

            val søknadId = JSONObject(createResponse.content).getJSONArray("stønadsperioder").getJSONObject(0).getJSONObject("søknad").getInt("id")

            defaultRequest(Get, "$søknadPath/$søknadId").apply {
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
            testEnv()
            testSusebakover(httpClients = buildClients(personOppslag = object : PersonOppslag {
                override fun person(ident: Fnr): ClientResponse = throw NotImplementedError()
                override fun aktørId(ident: Fnr): String = stubAktørId
            }))
        }) {
            handleRequest(Post, søknadPath) {
                addHeader(Authorization, Jwt.create())
                addHeader(ContentType, Json.toString())
                addHeader(XCorrelationId, correlationId)
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                val sakId = JSONObject(response.content).getInt("id")
                val søknadId = JSONObject(response.content).getJSONArray("stønadsperioder").getJSONObject(0).getJSONObject("søknad").getInt("id")

                val ourRecords = SuKafkaClientStub.sentRecords.filter { it.key == "$sakId" }
                val first = NySøknad.fromJson(ourRecords.first().value, emptyMap())
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
            testEnv()
            testSusebakover()
        }) {
            defaultRequest(Post, søknadPath) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                sakNr = JSONObject(response.content).getInt("id")
            }

            defaultRequest(Get, "$sakPath/$sakNr").apply {
                assertEquals(OK, response.status())
                assertTrue(JSONObject(response.content).getJSONArray("stønadsperioder").getJSONObject(0).getJSONObject("søknad").getJSONObject("json").similar(JSONObject(soknadJson(fnr))))
            }
        }
    }
}
