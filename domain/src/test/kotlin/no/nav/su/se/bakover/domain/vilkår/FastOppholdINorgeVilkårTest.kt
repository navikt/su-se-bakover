package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeJuli2021
import no.nav.su.se.bakover.test.periodeJuni2021
import no.nav.su.se.bakover.test.periodeMai2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FastOppholdINorgeVilkårTest {
    @Test
    fun `mapper behandlingsinformasjon for oppfylt til vilkår og grunnlag`() {
        FastOppholdINorgeVilkår.tryCreate(
            periode = periode2021,
            fastOpphold = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
                begrunnelse = "jabadoo",
            ),
            clock = fixedClock,
        ).erLik(
            FastOppholdINorgeVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeFastOppholdINorge.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = FastOppholdINorgeGrunnlag(
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
        FastOppholdINorgeVilkår.tryCreate(
            periode = periode2021,
            fastOpphold = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
                begrunnelse = null,
            ),
            clock = fixedClock,
        ).erLik(
            FastOppholdINorgeVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeFastOppholdINorge.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Avslag,
                        grunnlag = FastOppholdINorgeGrunnlag(
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
        FastOppholdINorgeVilkår.tryCreate(
            periode = periode2021,
            fastOpphold = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.Uavklart,
                begrunnelse = null,
            ),
            clock = fixedClock,
        ).erLik(FastOppholdINorgeVilkår.IkkeVurdert) shouldBe true
    }

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = FastOppholdINorgeGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(periodeJuli2021, "")).erLik(
            FastOppholdINorgeVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeFastOppholdINorge.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = FastOppholdINorgeGrunnlag(
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
        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = FastOppholdINorgeGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = FastOppholdINorgeGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ) shouldBe FastOppholdINorgeVilkår.Vurdert.UgyldigFastOppholdINorgeVikår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeFastOppholdINorge.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = FastOppholdINorgeGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = periodeMai2021,
            ),
            vurderingsperiode = periodeMai2021,
            begrunnelse = "jabadoo",
        ).getOrFail()

        val v2 = VurderingsperiodeFastOppholdINorge.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = FastOppholdINorgeGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = periodeJuni2021,
            ),
            vurderingsperiode = periodeJuni2021,
            begrunnelse = null,
        ).getOrFail()

        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periodeMai2021)
            .erLik(FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periodeJuni2021)
            .erLik(FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periode2021)
            .erLik(FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }
}
