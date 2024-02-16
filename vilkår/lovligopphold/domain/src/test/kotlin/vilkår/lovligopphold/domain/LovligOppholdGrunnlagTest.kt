package vilkår.lovligopphold.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyLovligoppholGrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LovligOppholdGrunnlagTest {
    @Test
    fun `oppdaterer periode`() {
        nyLovligoppholGrunnlag().let {
            it.oppdaterPeriode(februar(2021)) shouldBe LovligOppholdGrunnlag(
                id = it.id,
                opprettet = it.opprettet,
                periode = februar(2021),
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        nyLovligoppholGrunnlag().copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }

        nyLovligoppholGrunnlag().copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        nyLovligoppholGrunnlag().erLik(
            LovligOppholdGrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
            ),
        ) shouldBe true
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlag = nyLovligoppholGrunnlag()

        grunnlag.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(grunnlag, LovligOppholdGrunnlag::id)
            it.id shouldNotBe grunnlag.id
        }
    }
}
