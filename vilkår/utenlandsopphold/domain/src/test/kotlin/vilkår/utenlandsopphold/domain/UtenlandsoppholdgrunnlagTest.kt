package vilkår.utenlandsopphold.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyUtenlandsoppholdgrunnlag
import org.junit.jupiter.api.Test
import vilkår.utenlandsopphold.domain.vilkår.Utenlandsoppholdgrunnlag
import java.util.UUID

internal class UtenlandsoppholdgrunnlagTest {
    @Test
    fun `oppdaterer periode`() {
        Utenlandsoppholdgrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).let {
            it.oppdaterPeriode(februar(2021)) shouldBe Utenlandsoppholdgrunnlag(
                id = it.id,
                opprettet = it.opprettet,
                periode = februar(2021),
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        Utenlandsoppholdgrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }

        Utenlandsoppholdgrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        Utenlandsoppholdgrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).erLik(
            Utenlandsoppholdgrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
            ),
        ) shouldBe true
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlag = nyUtenlandsoppholdgrunnlag()
        grunnlag.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(grunnlag, Utenlandsoppholdgrunnlag::id)
            it.id shouldNotBe grunnlag.id
        }
    }
}
