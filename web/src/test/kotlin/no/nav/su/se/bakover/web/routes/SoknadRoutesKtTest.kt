package no.nav.su.se.bakover.web.routes

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldMatch
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
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.build
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.personopplysninger
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.client.stubs.SuKafkaClientStub
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.Jwt
import no.nav.su.se.bakover.web.buildClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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

            val sakDto = objectMapper.readValue<SakJson>(createResponse.content!!)
            val søknad = sakDto.stønadsperioder.first().søknad
            defaultRequest(Get, "$søknadPath/${søknad.id}").apply {
                assertEquals(OK, response.status())
                soknadJson(fnr) shouldMatchJson søknad.json.toSøknadInnhold().toJson()
            }
        }
    }

    @Test
    fun `produserer kafka hendelse når søknad lagres på sak`() {
        val fnr = Fnr("01010100002")
        val correlationId = "my random UUID or something"
        val kafkaClient = SuKafkaClientStub()
        withTestApplication({
            testEnv()
            testSusebakover(httpClients = buildClients(personOppslag = object : PersonOppslag {
                override fun person(ident: Fnr): ClientResponse = throw NotImplementedError()
                override fun aktørId(ident: Fnr): String = stubAktørId
            }), kafkaClient = kafkaClient)
        }) {
            handleRequest(Post, søknadPath) {
                addHeader(Authorization, Jwt.create())
                addHeader(ContentType, Json.toString())
                addHeader(XCorrelationId, correlationId)
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                val sakId = JSONObject(response.content).getInt("id")
                val søknadId = JSONObject(response.content)
                    .getJSONArray("stønadsperioder")
                    .getJSONObject(0)
                    .getJSONObject("søknad")
                    .getInt("id")

                val ourRecords = kafkaClient.sentRecords.filter { it.key == "$sakId" }
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
        var sakNr: Long
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            defaultRequest(Post, søknadPath) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
                sakNr = objectMapper.readValue<SakJson>(response.content!!).id
            }

            defaultRequest(Get, "$sakPath/$sakNr").apply {
                assertEquals(OK, response.status())
                val sakJson = objectMapper.readValue<SakJson>(response.content!!)
                sakJson.stønadsperioder shouldHaveSize 1
                sakJson.stønadsperioder.first().søknad.json.personopplysninger.fnr shouldMatch fnr.toString()
            }
        }
    }
}
