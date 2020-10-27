package no.nav.su.se.bakover.web.routes.søknad.lukk

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.søknad.LukkSøknadRequest
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LukkSøknadInputHandlerTest {

    private val trukketJson = objectMapper.writeValueAsString(TrukketJson(datoSøkerTrakkSøknad = 1.oktober(2020)))

    @Test
    fun `godtar ikke ufullstendige requester`() {
        runBlocking {
            LukkSøknadInputHandler.handle(
                type = null,
                body = trukketJson,
                søknadId = UUID.randomUUID(),
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }

        runBlocking {
            LukkSøknadInputHandler.handle(
                type = "TRUKKET",
                body = null,
                søknadId = UUID.randomUUID(),
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }

        runBlocking {
            LukkSøknadInputHandler.handle(
                type = "FUNKERIIKKE",
                body = trukketJson,
                søknadId = UUID.randomUUID(),
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }

        runBlocking {
            LukkSøknadInputHandler.handle(
                type = "TRUKKET",
                body = """{"bogus":"json"}""",
                søknadId = UUID.randomUUID(),
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }
    }

    @Test
    fun `godtar fullstendige requester`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                type = "TRUKKET",
                body = trukketJson,
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123")
            ) shouldBe LukkSøknadRequest.TrekkSøknad(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
                trukketDato = 1.oktober(2020)
            ).right()
        }
    }
}
