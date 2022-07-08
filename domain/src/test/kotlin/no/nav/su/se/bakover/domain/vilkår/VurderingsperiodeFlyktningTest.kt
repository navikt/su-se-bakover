package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VurderingsperiodeFlyktningTest {

    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).let {
            it.oppdaterStønadsperiode(
                Stønadsperiode.create(februar(2021)),
            ) shouldBe VurderingsperiodeFlyktning.create(
                id = vilkårId,
                opprettet = fixedTidspunkt,
                vurdering = Vurdering.Innvilget,
                periode = februar(2021),
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }

        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).erLik(
            VurderingsperiodeFlyktning.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                vurdering = Vurdering.Innvilget,
                periode = februar(2021),
            ),
        ) shouldBe true

        VurderingsperiodeFlyktning.create(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            periode = år(2021),
        ).erLik(
            VurderingsperiodeFlyktning.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                vurdering = Vurdering.Avslag,
                periode = februar(2021),
            ),
        ) shouldBe false
    }
}
