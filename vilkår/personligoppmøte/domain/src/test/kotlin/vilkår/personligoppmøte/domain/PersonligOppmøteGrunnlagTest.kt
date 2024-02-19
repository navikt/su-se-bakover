package vilkår.personligoppmøte.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyPersonligoppmøteGrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class PersonligOppmøteGrunnlagTest {
    @Test
    fun `oppdaterer periode`() {
        PersonligOppmøteGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            årsak = PersonligOppmøteÅrsak.MøttPersonlig,
        ).let {
            it.oppdaterPeriode(februar(2021)) shouldBe PersonligOppmøteGrunnlag(
                id = it.id,
                opprettet = it.opprettet,
                periode = februar(2021),
                årsak = PersonligOppmøteÅrsak.MøttPersonlig,
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        PersonligOppmøteGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            årsak = PersonligOppmøteÅrsak.MøttPersonlig,
        ).copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }

        PersonligOppmøteGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            årsak = PersonligOppmøteÅrsak.MøttPersonlig,
        ).copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        PersonligOppmøteGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            årsak = PersonligOppmøteÅrsak.MøttPersonlig,
        ).erLik(
            PersonligOppmøteGrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
                årsak = PersonligOppmøteÅrsak.MøttPersonlig,
            ),
        ) shouldBe true
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlag = nyPersonligoppmøteGrunnlag()

        grunnlag.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(grunnlag, PersonligOppmøteGrunnlag::id)
            it.id shouldNotBe grunnlag.id
        }
    }
}
