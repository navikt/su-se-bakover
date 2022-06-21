package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
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
                opprettet = Tidspunkt.now(),
                periode = februar(2021),
            ),
        ) shouldBe true
    }
}
