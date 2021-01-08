package no.nav.su.se.bakover.web.routes.søknad.lukk

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.LukketSøknad
import no.nav.su.se.bakover.web.Resultat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LukkSøknadErrorHandlerTest {

    @Test
    fun `mapping av lukket søknad til resultat`() {
        val sakId = UUID.randomUUID()
        val saksnummer = Math.random().toLong()
        val sak = Sak(
            id = sakId,
            saksnummer = Saksnummer(saksnummer),
            opprettet = Tidspunkt.EPOCH,
            fnr = Fnr("12345678901"),
            søknader = emptyList(),
            behandlinger = emptyList(),
            utbetalinger = emptyList()
        )
        LukkSøknadErrorHandler.lukketSøknadResponse(LukketSøknad.UtenMangler(sak)).httpCode shouldBe HttpStatusCode.OK
        LukkSøknadErrorHandler.lukketSøknadResponse(LukketSøknad.MedMangler.KunneIkkeDistribuereBrev(sak)).httpCode shouldBe HttpStatusCode.OK
        LukkSøknadErrorHandler.lukketSøknadResponse(LukketSøknad.MedMangler.KunneIkkeLukkeOppgave(sak)).httpCode shouldBe HttpStatusCode.OK
    }

    @Test
    fun `returnerer feilmelding basert på oppstått feilsituasjon`() {
        val medBrevRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
            søknadId = UUID.randomUUID(),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
            trukketDato = 1.oktober(2020)
        )
        assertSoftly {

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.SøknadErAlleredeLukket
            ) shouldBe Resultat.message(
                HttpStatusCode.BadRequest,
                "Søknad med id ${medBrevRequest.søknadId} er allerede trukket"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.SøknadHarEnBehandling
            ) shouldBe Resultat.message(
                HttpStatusCode.BadRequest,
                "Søknad med id ${medBrevRequest.søknadId} har en aktiv behandling og kan derfor ikke lukkes"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                medBrevRequest,
                KunneIkkeLukkeSøknad.FantIkkeSøknad
            ) shouldBe Resultat.message(
                httpCode = HttpStatusCode.NotFound,
                message = "Fant ikke søknad med id ${medBrevRequest.søknadId}"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev
            ) shouldBe Resultat.message(
                HttpStatusCode.InternalServerError,
                "Kunne ikke journalføre brev"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.UgyldigTrukketDato
            ) shouldBe Resultat.message(
                HttpStatusCode.BadRequest,
                "Ugyldig lukket dato. Dato må være etter opprettet og kan ikke være frem i tid"
            )
        }
    }
}
