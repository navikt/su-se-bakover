package vilkår.flyktning.domain

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vilkår.flyktningVilkårInnvilget
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import java.util.UUID

internal class FlyktningVilkårTest {

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        FlyktningVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeFlyktning.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    periode = år(2021),
                ),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(juli(2021))).erLik(
            FlyktningVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeFlyktning.create(
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
        FlyktningVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeFlyktning.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    periode = år(2021),
                ),
                VurderingsperiodeFlyktning.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    vurdering = Vurdering.Innvilget,
                    periode = år(2021),
                ),
            ),
        ) shouldBe FlyktningVilkår.Vurdert.UgyldigFlyktningVilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeFlyktning.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            periode = mai(2021),
        )

        val v2 = VurderingsperiodeFlyktning.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            periode = juni(2021),
        )

        FlyktningVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(mai(2021))
            .erLik(FlyktningVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        FlyktningVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(juni(2021))
            .erLik(FlyktningVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        FlyktningVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(år(2021))
            .erLik(FlyktningVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }

    @Test
    fun `kopierer innholdet med ny id'er`() {
        val vilkår = flyktningVilkårInnvilget()

        vilkår.copyWithNewId().let {
            it.vurderingsperioder.size shouldBe 1
            it.vurderingsperioder.first().id shouldNotBe vilkår.vurderingsperioder.first().id
            it.vurderingsperioder.first().grunnlag?.id shouldBe vilkår.vurderingsperioder.first().grunnlag?.id
        }
    }
}
