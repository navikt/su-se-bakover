package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test

internal class OpprettRevurderingTest {

    @Test
    fun `kan opprette revurdering dersom det ikke finnes eksisterende åpne behandlinger`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre(stønadsperiode = stønadsperiode2021)).first

        sakUtenÅpenBehandling.opprettRevurdering(
            command = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakUtenÅpenBehandling.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `kan ikke opprette revurdering dersom det finnes en åpen revurdering`() {
        val sakMedÅpenRevurdering = opprettetRevurdering().first

        sakMedÅpenRevurdering.opprettRevurdering(
            command = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakMedÅpenRevurdering.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeOppretteRevurdering.HarÅpenBehandling.left()
    }

    @Test
    fun `kan ikke opprette revurdering dersom det finnes en åpen regulering`() {
        val sakMedÅpenRegulering = innvilgetSøknadsbehandlingMedÅpenRegulering(1.mai(2021)).first
        sakMedÅpenRegulering.opprettRevurdering(
            command = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakMedÅpenRegulering.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeOppretteRevurdering.HarÅpenBehandling.left()
    }
}
