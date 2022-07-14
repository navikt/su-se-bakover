package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteÅrsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VurderingsperiodePersonligOppmøteTest {
    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodePersonligOppmøte(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
                årsak = PersonligOppmøteÅrsak.MøttPersonlig,
            ),
            periode = år(2021),
        )
            .let {
                it.oppdaterStønadsperiode(
                    Stønadsperiode.create(februar(2021)),
                ) shouldBe VurderingsperiodePersonligOppmøte(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    grunnlag = PersonligOppmøteGrunnlag(
                        id = grunnlagId,
                        opprettet = fixedTidspunkt,
                        periode = februar(2021),
                        årsak = PersonligOppmøteÅrsak.MøttPersonlig,
                    ),
                    periode = februar(2021),
                )
            }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        VurderingsperiodePersonligOppmøte(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
                årsak = PersonligOppmøteÅrsak.MøttPersonlig,
            ),
            periode = år(2021),
        )
            .copy(CopyArgs.Tidslinje.Full).let {
                it shouldBe it.copy()
            }

        VurderingsperiodePersonligOppmøte(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
                årsak = PersonligOppmøteÅrsak.MøttPersonlig,
            ),
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodePersonligOppmøte(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
                årsak = PersonligOppmøteÅrsak.MøttPersonlig,
            ),
            periode = år(2021),
        ).erLik(
            VurderingsperiodePersonligOppmøte(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                grunnlag = PersonligOppmøteGrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    periode = februar(2021),
                    årsak = PersonligOppmøteÅrsak.MøttPersonlig,
                ),
                periode = februar(2021),
            ),
        ) shouldBe true

        VurderingsperiodePersonligOppmøte(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
                årsak = PersonligOppmøteÅrsak.MøttPersonlig,
            ),
            periode = år(2021),
        ).erLik(
            VurderingsperiodePersonligOppmøte(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                grunnlag = PersonligOppmøteGrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    periode = februar(2021),
                    årsak = PersonligOppmøteÅrsak.IkkeMøttPersonlig,
                ),
                periode = februar(2021),
            ),
        ) shouldBe false
    }
}
