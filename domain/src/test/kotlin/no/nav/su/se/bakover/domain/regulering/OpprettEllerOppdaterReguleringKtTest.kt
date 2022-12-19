package no.nav.su.se.bakover.domain.regulering

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test

internal class OpprettEllerOppdaterReguleringKtTest {
    @Test
    fun `oppretter regulering dersom det ikke finnes eksisterende åpne behandlinger`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre()).first
        sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(1.mai(2020), fixedClock).shouldBeRight()

        val sakMedÅpenSøknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().first
        sakMedÅpenSøknadsbehandling.opprettEllerOppdaterRegulering(1.mai(2020), fixedClock).shouldBeLeft()

        val sakMedÅpenRevurdering = opprettetRevurdering().first
        sakMedÅpenRevurdering.opprettEllerOppdaterRegulering(1.mai(2020), fixedClock).shouldBeLeft()
    }
}
