package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.revurderingUnderkjent
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold
import org.junit.jupiter.api.Test
import vilkår.domain.Vurdering

class LeggTilUtenlandsoppholdTest {

    @Test
    fun `får ikke legge til opphold i utlandet utenfor perioden`() {
        val uavklart = opprettetRevurdering().second

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = utenlandsoppholdInnvilget(
                periode = januar(2020),
            ),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = null,
                        periode = uavklart.periode,
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = null,
                        periode = januar(2020),
                    ),
                ),
            ).getOrFail(),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = utenlandsoppholdInnvilget(
                periode = uavklart.periode,
            ),
        ).shouldBeRight()
    }

    @Test
    fun `får bare lagt til opphold i utlandet for enkelte typer`() {
        val clock = tikkendeFixedClock()
        listOf(
            opprettetRevurdering(
                clock = clock,
            ),
            beregnetRevurdering(
                clock = clock,
            ),
            beregnetRevurdering(
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ),
            simulertRevurdering(
                clock = clock,
            ),
            simulertRevurdering(
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ),
            revurderingUnderkjent(
                clock = clock,
            ),
        ).map {
            it.second
        }.forEach {
            it.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
                utenlandsoppholdInnvilget(
                    opprettet = Tidspunkt.now(clock),
                ),
            ).let { oppdatert ->
                oppdatert.getOrFail() shouldBe beOfType<OpprettetRevurdering>()
            }
        }

        listOf(
            revurderingTilAttestering(),
            revurderingTilAttestering(vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag())),
            iverksattRevurdering().let { it.first to it.second.shouldBeType<IverksattRevurdering.Innvilget>() },
            iverksattRevurdering(vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag())).let { (it.first to it.second) },
            avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
        ).map {
            it.second
        }.forEach {
            it.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
                utenlandsoppholdInnvilget(),
            ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                it::class,
                OpprettetRevurdering::class,
            ).left()
        }
    }

    @Test
    fun `får ikke legge til vurderingsperioder med både avslag og innvilget`() {
        val uavklart = opprettetRevurdering().second

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = null,
                        periode = januar(2021),
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
                        grunnlag = null,
                        periode = Periode.create(1.februar(2021), 31.desember(2021)),
                    ),
                ),
            ).getOrFail(),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat.left()
    }

    @Test
    fun `må vurdere hele revurderingsperioden`() {
        val uavklart = opprettetRevurdering().second

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = null,
                        periode = januar(2021),
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = null,
                        periode = februar(2021),
                    ),
                ),
            ).getOrFail(),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden.left()
    }
}
