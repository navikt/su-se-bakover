package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VurderingsperiodeInstitusjonsoppholdTest {

    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeInstitusjonsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = InstitusjonsoppholdGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .let {
                it.oppdaterStønadsperiode(
                    Stønadsperiode.create(februar(2021)),
                ) shouldBe VurderingsperiodeInstitusjonsopphold.tryCreate(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = InstitusjonsoppholdGrunnlag(
                        id = grunnlagId,
                        opprettet = fixedTidspunkt,
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                ).getOrFail()
            }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        VurderingsperiodeInstitusjonsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = InstitusjonsoppholdGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .copy(CopyArgs.Tidslinje.Full).let {
                it shouldBe it.copy()
            }

        VurderingsperiodeInstitusjonsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = InstitusjonsoppholdGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail().copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodeInstitusjonsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = InstitusjonsoppholdGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .erLik(
                VurderingsperiodeInstitusjonsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Innvilget,
                    grunnlag = InstitusjonsoppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                ).getOrFail(),
            ) shouldBe true

        VurderingsperiodeInstitusjonsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = InstitusjonsoppholdGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .erLik(
                VurderingsperiodeInstitusjonsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Avslag,
                    grunnlag = InstitusjonsoppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                ).getOrFail(),
            ) shouldBe false
    }
}
