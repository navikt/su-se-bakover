package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LovligOppholdVilkårTest {
    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        lovligOppholdVilkårInnvilget().oppdaterStønadsperiode(Stønadsperiode.create(juli(2021))).erLik(
            lovligOppholdVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeLovligOppholdInnvilget(vurderingsperiode = juli(2021))),
            ),
        )
    }

    @Test
    fun `godtar ikke overlappende vurderingsperioder`() {
        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                vurderingsperiodeLovligOppholdInnvilget(),
                vurderingsperiodeLovligOppholdInnvilget(),
            ),
        ) shouldBe KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeLovligOpphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = mai(2021),
        )

        val v2 = VurderingsperiodeLovligOpphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = juni(2021),
        )

        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(mai(2021))
            .erLik(LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(juni(2021))
            .erLik(LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        LovligOppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(år(2021))
            .erLik(LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }
}
