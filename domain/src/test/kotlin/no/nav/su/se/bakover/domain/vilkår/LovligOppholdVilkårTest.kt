package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeJuli2021
import no.nav.su.se.bakover.test.periodeJuni2021
import no.nav.su.se.bakover.test.periodeMai2021
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LovligOppholdVilkårTest {
    @Test
    fun `mapper behandlingsinformasjon for oppfylt til vilkår og grunnlag`() {
        Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
            begrunnelse = "jabadoo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).erLik(
            LovligOppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeLovligOpphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = LovligOppholdGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = periode2021,
                        ),
                        vurderingsperiode = periode2021,
                        begrunnelse = "jabadoo",
                    ).getOrFail(),
                ),
            ).getOrFail(),
        ) shouldBe true
    }

    @Test
    fun `mapper behandlingsinformasjon for avslag til vilkår og grunnlag`() {
        Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt,
            begrunnelse = null,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).erLik(
            LovligOppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeLovligOpphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Avslag,
                        grunnlag = LovligOppholdGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = periode2021,
                        ),
                        vurderingsperiode = periode2021,
                        begrunnelse = "",
                    ).getOrFail(),
                ),
            ).getOrFail(),
        ) shouldBe true
    }

    @Test
    fun `mapper behandlingsinformasjon for uavklart til vilkår og grunnlag`() {
        Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.Uavklart,
            begrunnelse = null,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).erLik(LovligOppholdVilkår.IkkeVurdert) shouldBe true
    }

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeLovligOpphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = LovligOppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(periodeJuli2021, "")).erLik(
            LovligOppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeLovligOpphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = LovligOppholdGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = periodeJuli2021,
                        ),
                        vurderingsperiode = periodeJuli2021,
                        begrunnelse = "jabadoo",
                    ).getOrFail(),
                ),
            ).getOrFail(),
        )
    }

    @Test
    fun `godtar ikke overlappende vurderingsperioder`() {
        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeLovligOpphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = LovligOppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
                VurderingsperiodeLovligOpphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = LovligOppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ) shouldBe LovligOppholdVilkår.Vurdert.UgyldigLovligOppholdVilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeLovligOpphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = LovligOppholdGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = periodeMai2021,
            ),
            vurderingsperiode = periodeMai2021,
            begrunnelse = "jabadoo",
        ).getOrFail()

        val v2 = VurderingsperiodeLovligOpphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = LovligOppholdGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = periodeJuni2021,
            ),
            vurderingsperiode = periodeJuni2021,
            begrunnelse = null,
        ).getOrFail()

        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periodeMai2021)
            .erLik(LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periodeJuni2021)
            .erLik(LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periode2021)
            .erLik(LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }
}
