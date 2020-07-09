package no.nav.su.se.bakover.web.routes.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.string.shouldMatch
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.build
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.personopplysninger
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SøknadRoutesKtTest {

    fun soknadJson(fnr: Fnr) = build(personopplysninger = personopplysninger(fnr = fnr.toString())).toJson()

    @Test
    fun `lagrer og henter søknad`() {
        val fnr = Fnr("01010100001")
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val createResponse = defaultRequest(
                Post,
                søknadPath
            ) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson(fnr))
            }.apply {
                assertEquals(Created, response.status())
            }.response

            val sakDto = objectMapper.readValue<SakJson>(createResponse.content!!)
            val søknad = sakDto.søknader.first()
            defaultRequest(Get, "$søknadPath/${søknad.id}").apply {
                assertEquals(OK, response.status())
                soknadJson(fnr) shouldMatchJson søknad.søknadInnhold.toSøknadInnhold().toJson()
            }
        }
    }

    @Test
    fun `knytter søknad til sak ved innsending`() {
        val fnr = Fnr("01010100004")
        var sakNr: String
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
                sakJson.søknader.first().søknadInnhold.personopplysninger.fnr shouldMatch fnr.toString()
            }
        }
    }
}
