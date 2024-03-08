package vilkår.utenlandsopphold.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.nyVurderingsperiodeUtenlandsopphold
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import vilkår.utenlandsopphold.domain.vilkår.Utenlandsoppholdgrunnlag
import vilkår.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold
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

    @Test
    fun `kopierer innholdet med ny id`() {
        val vurderingsperiode = nyVurderingsperiodeUtenlandsopphold()
        vurderingsperiode.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(
                vurderingsperiode,
                VurderingsperiodeUtenlandsopphold::id,
                VurderingsperiodeUtenlandsopphold::grunnlag,
            )
            it.id shouldNotBe vurderingsperiode.id
            it.grunnlag!!.shouldBeEqualToIgnoringFields(vurderingsperiode.grunnlag!!, Utenlandsoppholdgrunnlag::id)
            it.id shouldNotBe vurderingsperiode.grunnlag!!.id
        }
    }
}
