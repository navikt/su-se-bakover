package no.nav.su.se.bakover.web.routes.søknad.lukk

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.service.søknad.LukkSøknadRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class LukkSøknadInputHandlerTest {
    @Test
    fun `mangler body gir ugyldig request`() {
        runBlocking {
            LukkSøknadInputHandler.handle(
                body = null,
                søknadId = UUID.randomUUID(),
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }
    }

    @Test
    fun `ukjent body gir ugyldig request`() {
        runBlocking {
            LukkSøknadInputHandler.handle(
                body = """{"bogus":"json"}""",
                søknadId = UUID.randomUUID(),
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }
    }

    @Test
    fun `godtar fullstendige requester for trukket søknad`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"TRUKKET",
                        "datoSøkerTrakkSøknad": "2020-10-01"
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe LukkSøknadRequest.TrekkSøknad(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
                trukketDato = 1.oktober(2020)
            ).right()
        }
    }

    @Test
    fun `godtar fullstendige requester for bortfalt søknad`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"BORTFALT"
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe LukkSøknadRequest.BortfaltSøknad(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123")
            ).right()
        }
    }

    @Test
    fun `godtar fullstendige requester for avviste søknad`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"AVVIST",
                        "sendBrev": true
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe LukkSøknadRequest.AvvistSøknad(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
                sendBrev = true
            ).right()
        }
    }

    @Test
    fun `instansiering av input json fungerer bare med korrekt type`() {
        assertThrows<IllegalArgumentException> {
            LukketJson.TrukketJson(
                type = Søknad.LukketType.BORTFALT,
                datoSøkerTrakkSøknad = 1.oktober(2020)
            )
        }
        assertThrows<IllegalArgumentException> {
            LukketJson.BortfaltJson(type = Søknad.LukketType.TRUKKET)
        }
    }
}
