package no.nav.su.se.bakover.institusjonsopphold.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeInstitusjonsoppholdInnvilget
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import java.util.UUID

internal class VurderingsperiodeInstitusjonsoppholdTest {

    private val vilkårId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeInstitusjonsopphold.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).let {
            it.oppdaterStønadsperiode(
                Stønadsperiode.create(februar(2021)),
            ) shouldBe VurderingsperiodeInstitusjonsopphold.create(
                id = vilkårId,
                opprettet = fixedTidspunkt,
                vurdering = Vurdering.Innvilget,

                periode = februar(2021),
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        VurderingsperiodeInstitusjonsopphold.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe VurderingsperiodeInstitusjonsopphold.create(
                id = it.id,
                opprettet = it.opprettet,
                vurdering = it.vurdering,
                periode = it.periode,
            )
        }

        VurderingsperiodeInstitusjonsopphold.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe VurderingsperiodeInstitusjonsopphold.create(
                id = it.id,
                opprettet = it.opprettet,
                vurdering = it.vurdering,
                periode = mai(2021),
            )
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodeInstitusjonsopphold.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).erLik(
            VurderingsperiodeInstitusjonsopphold.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                vurdering = Vurdering.Innvilget,
                periode = februar(2021),
            ),
        ) shouldBe true

        VurderingsperiodeInstitusjonsopphold.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).erLik(
            VurderingsperiodeInstitusjonsopphold.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                vurdering = Vurdering.Avslag,
                periode = februar(2021),
            ),
        ) shouldBe false
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val vurderingsperiode = vurderingsperiodeInstitusjonsoppholdInnvilget()

        vurderingsperiode.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(vurderingsperiode, VurderingsperiodeInstitusjonsopphold::id)
            it.id shouldNotBe vurderingsperiode.id
        }
    }
}
