package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.test.bortfallSøknad
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.søknadId
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTrukket
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test

internal class LukketSøknadsbehandlingTest {

    @Test
    fun `skal ikke kunne lukke en lukket søknadsbehandling`() {
        LukketSøknadsbehandling.tryCreate(
            søknadsbehandlingSomSkalLukkes = søknadsbehandlingTrukket().second,
            lukkSøknadCommand = bortfallSøknad(),
        ) shouldBe KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling.left()
    }

    @Test
    fun `skal ikke kunne lukke en søknadsbehandling til attestering`() {
        LukketSøknadsbehandling.tryCreate(
            søknadsbehandlingSomSkalLukkes = søknadsbehandlingTilAttesteringInnvilget().second,
            lukkSøknadCommand = bortfallSøknad(),
        ) shouldBe KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering.left()
    }

    @Test
    fun `skal ikke kunne lukke en iverksatt søknadsbehandling`() {
        LukketSøknadsbehandling.tryCreate(
            søknadsbehandlingSomSkalLukkes = søknadsbehandlingTilAttesteringInnvilget().second,
            lukkSøknadCommand = bortfallSøknad(),
        ) shouldBe KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering.left()
    }

    @Test
    fun `skal kunne lukke en opprettet søknadsbehandling uten stønadsperiode`() {
        nySøknadsbehandlingUtenStønadsperiode().let {(sak,søknadsbehandling)->
            søknadsbehandling.lukkSøknadsbehandlingOgSøknad(
                bortfallSøknad(søknadId),
            ) shouldBe LukketSøknadsbehandling.createFromPersistedState(
                søknadsbehandling = søknadsbehandling,
                søknad = Søknad.Journalført.MedOppgave.Lukket.Bortfalt(
                    id = søknadsbehandling.søknad.id,
                    opprettet = søknadsbehandling.søknad.opprettet,
                    sakId = sak.id,
                    søknadInnhold = søknadsbehandling.søknad.søknadInnhold,
                    journalpostId = søknadsbehandling.søknad.journalpostId,
                    oppgaveId = søknadsbehandling.søknad.oppgaveId,
                    lukketTidspunkt = fixedTidspunkt,
                    lukketAv = saksbehandler,
                    innsendtAv = veileder,
                ),
            ).right()
        }
    }
}
