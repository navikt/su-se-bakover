package no.nav.su.se.bakover.web.routes.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class SøknadRoutesKtTest {
    private val fnr = Fnr("01010100001")

    private val søknadInnhold: SøknadInnhold = build(
        personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(
            fnr = fnr.toString()
        )
    )

    private val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())
    private val objectRepo: ObjectRepo = DatabaseBuilder.build(EmbeddedDatabase.instance())

    @Test
    fun `lagrer og henter søknad`() {
        withTestApplication({
            testSusebakover()
        }) {
            val createResponse = defaultRequest(
                Post,
                søknadPath
            ) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                assertEquals(Created, response.status())
            }.response

            shouldNotThrow<Throwable> { objectMapper.readValue<SakJson>(createResponse.content!!) }

            val sakFraDb = objectRepo.hentSak(fnr)
            sakFraDb shouldNotBe null
            sakFraDb!!.søknader() shouldHaveAtLeastSize 1
        }
    }

    @Test
    fun `knytter søknad til sak ved innsending`() {
        var sakNr: String
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Post, søknadPath) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
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
