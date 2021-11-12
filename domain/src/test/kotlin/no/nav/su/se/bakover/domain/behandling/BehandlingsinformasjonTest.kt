package no.nav.su.se.bakover.domain.behandling

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.BehandlingsinformasjonTestData.behandlingsinformasjonMedAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import java.util.UUID
import no.nav.su.se.bakover.domain.behandling.BehandlingsinformasjonTestData as TestData

internal class BehandlingsinformasjonTest {

    @Test
    fun `nullstiller formue for eps dersom bosuituasjon oppdateres til ufullstendig uten eps`() {
        behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
        ).oppdaterBosituasjonOgEktefelleOgNullstillFormueForEpsHvisIngenEps(
            bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
        ) shouldBe behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltUtenEPS,
        ).right()
    }
}
