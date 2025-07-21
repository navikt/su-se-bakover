package no.nav.su.se.bakover.domain.revurdering

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.oppdater.oppdaterRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test

internal class OppdaterRevurderingTest {
    @Test
    fun `Omgjøring i oppdater krever en omgjøringsgrunn`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre(stønadsperiode = stønadsperiode2021)).first
        sakUtenÅpenBehandling.oppdaterRevurdering(
            command = OppdaterRevurderingCommand(
                revurderingId = revurderingId,
                periode = år(2021),
                årsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK.name,
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
            clock = fixedClock,
        ).shouldBeLeft().let {
            it shouldBe KunneIkkeOppdatereRevurdering.MåhaOmgjøringsgrunn
        }
    }
}
