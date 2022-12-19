package no.nav.su.se.bakover.domain.søknadsbehandling.opprett

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test

internal class OpprettNySøknadsbehandlingTest {
    @Test
    fun `oppretter søknadsbehandling dersom det ikke finnes eksisterende åpne behandlinger`() {
        val (sak, søknad) = nySakUføre()
        sak.opprettNySøknadsbehandling(søknad.id, fixedClock, saksbehandler).shouldBeRight()
    }

    @Test
    fun `Kan ikke opprette søknadsbehandling dersom det finnes en åpen søknadsbehandling`() {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart()
        sak.opprettNySøknadsbehandling(
            søknadsbehandling.søknad.id,
            fixedClock,
            saksbehandler,
        ) shouldBe Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenBehandling.left()
    }

    @Test
    fun `Kan ikke opprette søknadsbehandling dersom det finnes en åpen revurdering`() {
        val (sak, _) = opprettetRevurdering()
        sak.opprettNySøknadsbehandling(
            sak.søknader.first().id,
            fixedClock,
            saksbehandler,
        ) shouldBe Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenBehandling.left()
    }

    @Test
    fun `Kan ikke opprette søknadsbehandling dersom det finnes en åpen regulering `() {
        val (sak, _) = innvilgetSøknadsbehandlingMedÅpenRegulering(1.mai(2021))
        sak.opprettNySøknadsbehandling(
            sak.søknader.first().id,
            fixedClock,
            saksbehandler,
        ) shouldBe Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenBehandling.left()
    }
}
