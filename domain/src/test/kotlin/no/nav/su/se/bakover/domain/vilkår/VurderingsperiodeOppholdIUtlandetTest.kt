package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.OppholdIUtlandetGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeFebruar2021
import no.nav.su.se.bakover.test.periodeMai2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VurderingsperiodeOppholdIUtlandetTest {
    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeOppholdIUtlandet.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = OppholdIUtlandetGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .let {
                it.oppdaterStønadsperiode(
                    Stønadsperiode.create(
                        periodeFebruar2021,
                        "",
                    ),
                ) shouldBe VurderingsperiodeOppholdIUtlandet.tryCreate(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = OppholdIUtlandetGrunnlag(
                        id = grunnlagId,
                        opprettet = fixedTidspunkt,
                        periode = periodeFebruar2021,
                    ),
                    vurderingsperiode = periodeFebruar2021,
                    begrunnelse = null,
                ).getOrFail()
            }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        VurderingsperiodeOppholdIUtlandet.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = OppholdIUtlandetGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .copy(CopyArgs.Tidslinje.Full).let {
                it shouldBe it.copy()
            }

        VurderingsperiodeOppholdIUtlandet.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = OppholdIUtlandetGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail().copy(CopyArgs.Tidslinje.NyPeriode(periodeMai2021)).let {
            it shouldBe it.copy(periode = periodeMai2021)
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodeOppholdIUtlandet.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = OppholdIUtlandetGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodeOppholdIUtlandet.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Innvilget,
                    grunnlag = OppholdIUtlandetGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = periodeFebruar2021,
                    ),
                    vurderingsperiode = periodeFebruar2021,
                    begrunnelse = "koko",
                ).getOrFail(),
            ) shouldBe true

        VurderingsperiodeOppholdIUtlandet.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = OppholdIUtlandetGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodeOppholdIUtlandet.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Avslag,
                    grunnlag = OppholdIUtlandetGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = periodeFebruar2021,
                    ),
                    vurderingsperiode = periodeFebruar2021,
                    begrunnelse = "koko",
                ).getOrFail(),
            ) shouldBe false
    }
}
