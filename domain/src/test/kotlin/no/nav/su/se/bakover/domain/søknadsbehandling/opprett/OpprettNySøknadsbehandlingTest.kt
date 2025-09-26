package no.nav.su.se.bakover.domain.søknadsbehandling.opprett

import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySøknadPåEksisterendeSak
import org.junit.jupiter.api.Test

internal class OpprettNySøknadsbehandlingTest {

    @Test
    fun `oppretter søknadsbehandling`() {
        val (sak, søknad) = nySakUføre()
        sak.opprettNySøknadsbehandling(
            søknadId = søknad.id,
            clock = fixedClock,
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `Kan opprette søknadsbehandling dersom det finnes en åpen revurdering`() {
        val clock = TikkendeKlokke()
        val (sak, søknad) = nySøknadPåEksisterendeSak(
            eksisterendeSak = opprettetRevurdering(clock = clock).first,
            clock = clock,
        )
        sak.opprettNySøknadsbehandling(
            søknadId = søknad.id,
            clock = clock,
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `Kan opprette søknadsbehandling dersom det finnes en åpen regulering `() {
        val clock = TikkendeKlokke()
        val (sak, søknad) = nySøknadPåEksisterendeSak(
            clock = clock,
            eksisterendeSak = innvilgetSøknadsbehandlingMedÅpenRegulering(
                mai(year = 2021),
                clock = clock,
            ).first,
        )
        sak.opprettNySøknadsbehandling(
            søknadId = søknad.id,
            clock = clock,
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }
}
