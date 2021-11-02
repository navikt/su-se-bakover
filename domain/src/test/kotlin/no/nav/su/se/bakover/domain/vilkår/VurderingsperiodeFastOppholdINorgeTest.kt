package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeFebruar2021
import no.nav.su.se.bakover.test.periodeMai2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VurderingsperiodeFastOppholdINorgeTest {

    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = FastOppholdINorgeGrunnlag(
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
                ) shouldBe VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = FastOppholdINorgeGrunnlag(
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
        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = FastOppholdINorgeGrunnlag(
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

        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = FastOppholdINorgeGrunnlag(
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
        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = FastOppholdINorgeGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Innvilget,
                    grunnlag = FastOppholdINorgeGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = periodeFebruar2021,
                    ),
                    vurderingsperiode = periodeFebruar2021,
                    begrunnelse = "koko",
                ).getOrFail(),
            ) shouldBe true

        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = FastOppholdINorgeGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Avslag,
                    grunnlag = FastOppholdINorgeGrunnlag(
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
