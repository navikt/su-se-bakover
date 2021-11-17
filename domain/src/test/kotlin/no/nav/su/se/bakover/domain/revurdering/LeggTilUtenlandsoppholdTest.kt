package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.test.avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.utlandsoppholdInnvilget
import org.junit.jupiter.api.Test

class LeggTilUtenlandsoppholdTest {

    @Test
    fun `får ikke legge til opphold i utlandet utenfor perioden`() {
        val uavklart = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = utlandsoppholdInnvilget(
                periode = Periode.create(1.januar(2020), 31.januar(2020)),
            ),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = uavklart.periode,
                        begrunnelse = "begrunnelse",
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = Periode.create(1.januar(2020), 31.januar(2020)),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail(),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = utlandsoppholdInnvilget(
                periode = uavklart.periode,
            ),
        ).isRight() shouldBe true
    }

    @Test
    fun `får bare lagt til opphold i utlandet for enkelte typer`() {
        listOf(
            opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(),
            beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(),
            beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(),
        ).map {
            it.second
        }.forEach {
            it.oppdaterUtenlandsoppholdOgMarkerSomVurdert(utlandsoppholdInnvilget()).let { oppdatert ->
                oppdatert.isRight() shouldBe true
                oppdatert.getOrFail() shouldBe beOfType<OpprettetRevurdering>()
            }
        }

        listOf(
            tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(),
            tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(),
            iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
        ).map {
            it.second
        }.forEach {
            it.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
                utlandsoppholdInnvilget(),
            ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                it::class,
                OpprettetRevurdering::class,
            ).left()
        }
    }
}
