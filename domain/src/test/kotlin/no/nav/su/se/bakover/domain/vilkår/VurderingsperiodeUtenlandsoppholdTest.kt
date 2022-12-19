package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
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
            vurdering = Vurdering.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .let {
                it.oppdaterStønadsperiode(
                    Stønadsperiode.create(februar(2021)),
                ) shouldBe VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
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
        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .copy(CopyArgs.Tidslinje.Full).let {
                it shouldBe it.copy()
            }

        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
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
        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .erLik(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                ).getOrFail(),
            ) shouldBe true

        VurderingsperiodeUtenlandsopphold.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
        ).getOrFail()
            .erLik(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Avslag,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                ).getOrFail(),
            ) shouldBe false
    }
}
