package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class InstitusjonsoppholdVilkårTest {
    @Test
    fun `mapper behandlingsinformasjon for oppfylt til vilkår og grunnlag`() {
        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).erLik(
            InstitusjonsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeInstitusjonsopphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = InstitusjonsoppholdGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = år(2021),
                        ),
                        vurderingsperiode = år(2021),
                    ).getOrFail(),
                ),
            ).getOrFail(),
        ) shouldBe true
    }

    @Test
    fun `mapper behandlingsinformasjon for avslag til vilkår og grunnlag`() {
        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårIkkeOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).erLik(
            InstitusjonsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeInstitusjonsopphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Avslag,
                        grunnlag = InstitusjonsoppholdGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = år(2021),
                        ),
                        vurderingsperiode = år(2021),
                    ).getOrFail(),
                ),
            ).getOrFail(),
        ) shouldBe true
    }

    @Test
    fun `mapper behandlingsinformasjon for uavklart til vilkår og grunnlag`() {
        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.Uavklart,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).erLik(InstitusjonsoppholdVilkår.IkkeVurdert) shouldBe true
    }

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeInstitusjonsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = InstitusjonsoppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = år(2021),
                    ),
                    vurderingsperiode = år(2021),
                ).getOrFail(),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(juli(2021))).erLik(
            InstitusjonsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeInstitusjonsopphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = InstitusjonsoppholdGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = juli(2021),
                        ),
                        vurderingsperiode = juli(2021),
                    ).getOrFail(),
                ),
            ).getOrFail(),
        )
    }

    @Test
    fun `godtar ikke overlappende vurderingsperioder`() {
        InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeInstitusjonsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = InstitusjonsoppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = år(2021),
                    ),
                    vurderingsperiode = år(2021),
                ).getOrFail(),
                VurderingsperiodeInstitusjonsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = InstitusjonsoppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = år(2021),
                    ),
                    vurderingsperiode = år(2021),
                ).getOrFail(),
            ),
        ) shouldBe InstitusjonsoppholdVilkår.Vurdert.UgyldigInstitisjonsoppholdVilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeInstitusjonsopphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = InstitusjonsoppholdGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = mai(2021),
            ),
            vurderingsperiode = mai(2021),
        ).getOrFail()

        val v2 = VurderingsperiodeInstitusjonsopphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = InstitusjonsoppholdGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = juni(2021),
            ),
            vurderingsperiode = juni(2021),
        ).getOrFail()

        InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(mai(2021))
            .erLik(InstitusjonsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(juni(2021))
            .erLik(InstitusjonsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(år(2021))
            .erLik(InstitusjonsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }
}
