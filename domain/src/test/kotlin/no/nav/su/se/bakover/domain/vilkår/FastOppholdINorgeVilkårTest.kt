package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import vilkår.domain.Vurdering
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.fastopphold.domain.VurderingsperiodeFastOppholdINorge
import java.util.UUID

internal class FastOppholdINorgeVilkårTest {
    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    vurderingsperiode = år(2021),
                ).getOrFail(),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(juli(2021))).erLik(
            FastOppholdINorgeVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeFastOppholdINorge.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        vurdering = Vurdering.Innvilget,
                        vurderingsperiode = juli(2021),
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
                    vurdering = Vurdering.Innvilget,
                    vurderingsperiode = år(2021),
                ).getOrFail(),
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    vurderingsperiode = år(2021),
                ).getOrFail(),
            ),
        ) shouldBe FastOppholdINorgeVilkår.Vurdert.UgyldigFastOppholdINorgeVikår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeFastOppholdINorge.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = mai(2021),
        ).getOrFail()

        val v2 = VurderingsperiodeFastOppholdINorge.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = juni(2021),
        ).getOrFail()

        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(mai(2021))
            .erLik(FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(juni(2021))
            .erLik(FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        FastOppholdINorgeVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(år(2021))
            .erLik(FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }
}
