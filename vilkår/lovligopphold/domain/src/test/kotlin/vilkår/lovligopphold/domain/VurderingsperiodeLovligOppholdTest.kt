package vilkår.lovligopphold.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdInnvilget
import org.junit.jupiter.api.Test

internal class VurderingsperiodeLovligOppholdTest {
    @Test
    fun `oppdaterer periode`() {
        vurderingsperiodeLovligOppholdInnvilget().oppdaterStønadsperiode(
            Stønadsperiode.create(februar(2021)),
        ).periode shouldBe februar(2021)
    }

    @Test
    fun `kopierer korrekte verdier`() {
        vurderingsperiodeLovligOppholdInnvilget().copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }
        vurderingsperiodeLovligOppholdInnvilget().copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        vurderingsperiodeLovligOppholdInnvilget().erLik(vurderingsperiodeLovligOppholdInnvilget()) shouldBe true
        vurderingsperiodeLovligOppholdInnvilget().erLik(fastOppholdVilkårAvslag().vurderingsperioder.single()) shouldBe false
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val vurderingsperiode = vurderingsperiodeLovligOppholdInnvilget()

        vurderingsperiode.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(vurderingsperiode, VurderingsperiodeLovligOpphold::id)
            it.id shouldNotBe vurderingsperiode.id
        }
    }
}
