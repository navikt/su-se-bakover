package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LovligOppholdGrunnlagTest {
    @Test
    fun `oppdaterer periode`() {
        LovligOppholdGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).let {
            it.oppdaterPeriode(februar(2021)) shouldBe LovligOppholdGrunnlag(
                id = it.id,
                opprettet = it.opprettet,
                periode = februar(2021),
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        LovligOppholdGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }

        LovligOppholdGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        LovligOppholdGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        ).erLik(
            LovligOppholdGrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
            ),
        ) shouldBe true
    }
}
