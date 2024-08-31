package vilkår.flyktning.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeFlyktning
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import java.util.UUID

internal class VurderingsperiodeFlyktningTest {

    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).let {
            it.oppdaterStønadsperiode(
                Stønadsperiode.create(februar(2021)),
            ) shouldBe VurderingsperiodeFlyktning.create(
                id = vilkårId,
                opprettet = fixedTidspunkt,
                vurdering = Vurdering.Innvilget,
                periode = februar(2021),
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        val beforeCopy = VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        )
        val afterCopy = beforeCopy.copy(CopyArgs.Tidslinje.Full)
        afterCopy shouldBe VurderingsperiodeFlyktning.create(
            id = afterCopy.id,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        )

        val afterCopy2 = beforeCopy.copy(CopyArgs.Tidslinje.NyPeriode(mai(2021)))
        afterCopy2 shouldBe VurderingsperiodeFlyktning.create(
            id = afterCopy2.id,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = mai(2021),
        )
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).erLik(
            VurderingsperiodeFlyktning.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                vurdering = Vurdering.Innvilget,
                periode = februar(2021),
            ),
        ) shouldBe true

        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).erLik(
            VurderingsperiodeFlyktning.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                vurdering = Vurdering.Avslag,
                periode = februar(2021),
            ),
        ) shouldBe false
    }

    @Test
    fun `kopierer innholdet med ny id `() {
        val vurdering = vurderingsperiodeFlyktning()

        vurdering.copyWithNewId().let {
            it.id shouldNotBe vurdering.id
            it.opprettet shouldBe vurdering.opprettet
            it.vurdering shouldBe vurdering.vurdering
            it.periode shouldBe vurdering.periode
        }
    }
}
