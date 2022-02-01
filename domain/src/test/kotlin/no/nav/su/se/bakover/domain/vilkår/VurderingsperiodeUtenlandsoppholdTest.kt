package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeFebruar2021
import no.nav.su.se.bakover.test.periodeMai2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VurderingsperiodeUtenlandsoppholdTest {
    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
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
                ) shouldBe VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
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
        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
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

        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
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
        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = periodeFebruar2021,
                    ),
                    vurderingsperiode = periodeFebruar2021,
                    begrunnelse = "koko",
                ).getOrFail(),
            ) shouldBe true

        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            vurderingsperiode = periode2021,
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Avslag,
                    grunnlag = Utenlandsoppholdgrunnlag(
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
