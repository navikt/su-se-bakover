package vilkår.fastopphold.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyFastOppholdINorgeGrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FastOppholdINorgeGrunnlagTest {
    @Test
    fun `oppdaterer periode`() {
        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).let {
            it.oppdaterPeriode(februar(2021)) shouldBe FastOppholdINorgeGrunnlag(
                id = it.id,
                opprettet = it.opprettet,
                periode = februar(2021),
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }

        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).erLik(
            FastOppholdINorgeGrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
            ),
        ) shouldBe true
    }

    @Test
    fun `kopierer grunnlag med ny id`() {
        val grunnlag = nyFastOppholdINorgeGrunnlag()

        grunnlag.copyWithNewId().let {
            it.id shouldNotBe grunnlag.id
            it.periode shouldBe grunnlag.periode
            it.opprettet shouldBe grunnlag.opprettet
        }
    }
}
