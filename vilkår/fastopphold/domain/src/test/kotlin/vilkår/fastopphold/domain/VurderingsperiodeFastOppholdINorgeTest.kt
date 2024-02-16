package vilkår.fastopphold.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.nyVurderingsperiodeFastOppholdINorge
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import java.util.UUID

internal class VurderingsperiodeFastOppholdINorgeTest {

    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = år(2021),
        ).getOrFail()
            .let {
                it.oppdaterStønadsperiode(
                    Stønadsperiode.create(februar(2021)),
                ) shouldBe VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Innvilget,
                    vurderingsperiode = februar(2021),
                ).getOrFail()
            }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = år(2021),
        ).getOrFail()
            .copy(CopyArgs.Tidslinje.Full).let {
                it shouldBe it.copy()
            }

        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = år(2021),
        ).getOrFail().copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = år(2021),
        ).getOrFail()
            .erLik(
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Innvilget,
                    vurderingsperiode = februar(2021),
                ).getOrFail(),
            ) shouldBe true

        VurderingsperiodeFastOppholdINorge.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            vurderingsperiode = år(2021),
        ).getOrFail()
            .erLik(
                VurderingsperiodeFastOppholdINorge.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Avslag,
                    vurderingsperiode = februar(2021),
                ).getOrFail(),
            ) shouldBe false
    }

    @Test
    fun `kopierer vurderingsperioden med ny id`() {
        val vurderingsperiode = nyVurderingsperiodeFastOppholdINorge()

        vurderingsperiode.copyWithNewId().let {
            it.id shouldNotBe vurderingsperiode.id
            it.opprettet shouldBe vurderingsperiode.opprettet
            it.periode shouldBe vurderingsperiode.periode
            it.vurdering shouldBe vurderingsperiode.vurdering
            it.grunnlag shouldBe vurderingsperiode.grunnlag
        }
    }
}
