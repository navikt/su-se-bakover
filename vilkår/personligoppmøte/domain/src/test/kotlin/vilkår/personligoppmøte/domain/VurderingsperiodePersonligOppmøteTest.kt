package vilkår.personligoppmøte.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vurderingsperiode.nyVurderingsperodePersonligOppmøte
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
                opprettet = fixedTidspunkt,
                grunnlag = PersonligOppmøteGrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
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
                opprettet = fixedTidspunkt,
                grunnlag = PersonligOppmøteGrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = februar(2021),
                    årsak = PersonligOppmøteÅrsak.IkkeMøttPersonlig,
                ),
                periode = februar(2021),
            ),
        ) shouldBe false
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val vurderingsperiode = nyVurderingsperodePersonligOppmøte()
        vurderingsperiode.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(
                vurderingsperiode,
                VurderingsperiodePersonligOppmøte::id,
                VurderingsperiodePersonligOppmøte::grunnlag,
            )
            it.id shouldNotBe vurderingsperiode.id
            it.grunnlag.shouldBeEqualToIgnoringFields(vurderingsperiode.grunnlag, PersonligOppmøteGrunnlag::id)
            it.grunnlag shouldNotBe vurderingsperiode.grunnlag
        }
    }
}
