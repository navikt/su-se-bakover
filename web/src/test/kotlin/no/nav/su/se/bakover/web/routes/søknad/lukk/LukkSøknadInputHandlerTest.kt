package no.nav.su.se.bakover.web.routes.søknad.lukk

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.test.avvisSøknadMedBrev
import no.nav.su.se.bakover.test.avvisSøknadUtenBrev
import no.nav.su.se.bakover.test.bortfallSøknad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.trekkSøknad
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
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }
    }

    @Test
    fun `ukjent body gir ugyldig request`() {
        runBlocking {
            LukkSøknadInputHandler.handle(
                body = """{"bogus":"json"}""",
                søknadId = UUID.randomUUID(),
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
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
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = 1.oktober(2020).startOfDay().fixedClock(),
            ) shouldBe trekkSøknad(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
                trukketDato = 1.oktober(2020),
                lukketTidspunkt = 1.oktober(2020).startOfDay(),
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
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
            ) shouldBe bortfallSøknad(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
            ).right()
        }
    }

    @Test
    fun `godtar fullstendige requester for avviste søknad med brev, uten fritekst`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"AVVIST",
                        "brevConfig": {
                            "brevtype":"${LukketJson.BrevType.VEDTAK}"
                        }
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
            ) shouldBe avvisSøknadMedBrev(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
                brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst(),
            ).right()
        }
    }

    @Test
    fun `godtar fullstendige requester for avviste søknad med brev, med fritekst`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"AVVIST",
                        "brevConfig": {
                            "brevtype":"${LukketJson.BrevType.VEDTAK}",
                            "fritekst": "Jeg er fritekst"
                        }
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
            ) shouldBe avvisSøknadMedBrev(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
                brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst(),
            ).right()
        }
    }

    @Test
    fun `godtar fullstendige requester for avviste søknad uten brev`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"AVVIST"
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
            ) shouldBe avvisSøknadUtenBrev(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
            ).right()
        }
    }

    @Test
    fun `ugyldig brevtype for avviste søknad med brev`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"AVVIST",
                        "brevConfig": {
                            "brevtype":"FJAS"
                        }
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
            ) shouldBe UgyldigLukkSøknadRequest.left()
        }
    }

    @Test
    fun `instansiering av input json fungerer bare med korrekt type`() {
        assertThrows<IllegalArgumentException> {
            LukketJson.TrukketJson(
                type = LukketJson.LukketType.BORTFALT,
                datoSøkerTrakkSøknad = 1.oktober(2020),
            )
        }
        assertThrows<IllegalArgumentException> {
            LukketJson.AvvistJson(
                type = LukketJson.LukketType.BORTFALT,
            )
        }
        assertThrows<IllegalArgumentException> {
            LukketJson.BortfaltJson(type = LukketJson.LukketType.TRUKKET)
        }
    }

    @Test
    fun `godtar avvist søknad med fritekst`() {
        runBlocking {
            val søknadId = UUID.randomUUID()
            LukkSøknadInputHandler.handle(
                body = """
                    {
                        "type":"AVVIST",
                        "brevConfig": {
                            "brevtype":"${LukketJson.BrevType.FRITEKST}",
                            "fritekst": "jeg er fritekst"
                        }
                    }
                """.trimIndent(),
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
            ) shouldBe avvisSøknadMedBrev(
                søknadId = søknadId,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
                brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("jeg er fritekst"),
            ).right()
        }
    }
}
