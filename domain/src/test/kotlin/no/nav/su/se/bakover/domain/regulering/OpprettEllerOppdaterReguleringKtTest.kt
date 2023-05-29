package no.nav.su.se.bakover.domain.regulering

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test

internal class OpprettEllerOppdaterReguleringKtTest {
    @Test
    fun `oppretter regulering fra søknadsbehandlingsvedtak`() {
        // TODO jah: Dette bør feile siden stønaden ikke har endret seg.
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre()).first
        sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(mai(2020), fixedClock).shouldBeRight()
    }

    @Test
    fun `oppretter regulering fra revurdering`() {
        // TODO jah: Dette bør feile siden stønaden ikke har endret seg.
        val sakMedÅpenRevurdering = opprettetRevurdering().first
        sakMedÅpenRevurdering.opprettEllerOppdaterRegulering(mai(2020), fixedClock).shouldBeRight()
    }

    @Test
    fun `kan ikke regulere sak uten vedtak`() {
        val sakMedÅpenSøknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().first
        sakMedÅpenSøknadsbehandling.opprettEllerOppdaterRegulering(mai(2020), fixedClock).shouldBe(
            Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left(),
        )
    }
}
