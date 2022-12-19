package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

internal class InstitusjonsoppholdVilkårTest {

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeInstitusjonsopphold.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    periode = år(2021),
                ),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(juli(2021))).erLik(
            InstitusjonsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeInstitusjonsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        vurdering = Vurdering.Innvilget,
                        periode = juli(2021),
                    ),
                ),
            ).getOrFail(),
        )
    }

    @Test
    fun `godtar ikke overlappende vurderingsperioder`() {
        InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeInstitusjonsopphold.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    periode = år(2021),
                ),
                VurderingsperiodeInstitusjonsopphold.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    periode = år(2021),
                ),
            ),
        ) shouldBe InstitusjonsoppholdVilkår.Vurdert.UgyldigInstitisjonsoppholdVilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeInstitusjonsopphold.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            periode = mai(2021),
        )

        val v2 = VurderingsperiodeInstitusjonsopphold.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            periode = juni(2021),
        )

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
