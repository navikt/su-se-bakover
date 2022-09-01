package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.simulertRevurderingOpphørtPgaVilkårFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

internal class OpphørsdatoForUtbetalingerTest {

    @Test
    fun `opphørsdato er lik revurdering fra og med hvis ingen avkortingsvarsel`() {
        val revurderingsperiode =
            Periode.create(LocalDate.now(nåtidForSimuleringStub).startOfMonth(), 31.desember(2021))
        val simulert = simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = revurderingsperiode,
        ).second
        OpphørsdatoForUtbetalinger(simulert).value shouldBe revurderingsperiode
        simulert.avkorting shouldBe beOfType<AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående>()
    }

    @Test
    fun `opphørsdato er lik første måned uten utbetalte beløp hvis feilutbetalinger kan avkortes`() {
        val tidligsteFraOgMedSomIkkeErUtbetalt = LocalDate.now(nåtidForSimuleringStub).startOfMonth()
        val simulert = simulertRevurderingOpphørtPgaVilkårFraInnvilgetSøknadsbehandlingsVedtak(
            vilkårSomFørerTilOpphør = utenlandsoppholdAvslag(),
        ).second
        OpphørsdatoForUtbetalinger(simulert).value shouldBe Periode.create(tidligsteFraOgMedSomIkkeErUtbetalt, simulert.periode.tilOgMed)
        simulert.avkorting shouldBe beOfType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>()
    }

    @Test
    fun `kaster exception hvis opphørsdato for utbetalinger er utenfor revurderingsperioden`() {
        assertThrows<IllegalStateException> {
            simulertRevurderingOpphørtPgaVilkårFraInnvilgetSøknadsbehandlingsVedtak(
                revurderingsperiode = Periode.create(1.januar(2021), 31.mars(2021)),
                vilkårSomFørerTilOpphør = utenlandsoppholdAvslag(periode = Periode.create(1.januar(2021), 31.mars(2021))),
            ).second
        }.also {
            it.message shouldContain "Opphørsdato er utenfor revurderingsperioden"
        }
    }
}
