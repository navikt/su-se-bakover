package no.nav.su.se.bakover.web.routes.sak

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class SakRoutesKtTest {

    private val sakFnr01 = "12345678911"
    private val repos = DatabaseBuilder.build(EmbeddedDatabase.instance())
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()

    @Test
    fun `henter sak for sak id`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                )
        ) {
            SakFactory(clock = fixedClock).nySak(Fnr(sakFnr01), søknadInnhold).also {
                repos.sak.opprettSak(it)
            }
            val opprettetSakId: Sak = repos.sak.hentSak(Fnr(sakFnr01))!!

            defaultRequest(
                Get,
                "$sakPath/${opprettetSakId.id}",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `henter sak for fødselsnummer`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                )
        ) {
            repos.sak.opprettSak(SakFactory(clock = fixedClock).nySak(Fnr(sakFnr01), søknadInnhold))

            defaultRequest(
                Get,
                "$sakPath?fnr=$sakFnr01",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `error handling`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                )
        ) {
            defaultRequest(
                Get,
                sakPath,
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertEquals(BadRequest, response.status(), "$sakPath gir 400 ved manglende fnr")
            }

            defaultRequest(
                Get,
                "$sakPath?fnr=12341234123",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertEquals(NotFound, response.status(), "$sakPath?fnr= gir 404 ved ukjent fnr")
            }

            defaultRequest(
                Get,
                "$sakPath/${UUID.randomUUID()}",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertEquals(NotFound, response.status(), "$sakPath/UUID gir 404 ved ikke-eksisterende sak-ID")
            }

            defaultRequest(
                Get,
                "$sakPath/adad",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertEquals(BadRequest, response.status(), "$sakPath/UUID gir 400 ved ugyldig UUID")
            }
        }
    }
}
