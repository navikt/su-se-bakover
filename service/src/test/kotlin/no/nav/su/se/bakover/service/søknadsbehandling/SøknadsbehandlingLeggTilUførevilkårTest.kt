package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class SøknadsbehandlingLeggTilUførevilkårTest {
    @Test
    fun `happy path`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().second
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.søknadsbehandlingService.leggTilUførevilkår(
                request = LeggTilUførevurderingerRequest(
                    behandlingId = UUID.randomUUID(),
                    vurderinger = nonEmptyListOf(
                        LeggTilUførevilkårRequest(
                            behandlingId = UUID.randomUUID(),
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            uføregrad = Uføregrad.parse(50),
                            forventetInntekt = 15000,
                            oppfylt = UførevilkårStatus.VilkårOppfylt,
                            begrunnelse = "jambo",
                        ),
                        LeggTilUførevilkårRequest(
                            behandlingId = UUID.randomUUID(),
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 30000,
                            oppfylt = UførevilkårStatus.VilkårOppfylt,
                            begrunnelse = "jambo",
                        ),
                    ),
                ),
            ).getOrFail().let { medUførevilkår ->
                (medUførevilkår.vilkårsvurderinger.uføreVilkår().getOrFail() as UføreVilkår.Vurdert).let {
                    it.vurderingsperioder shouldHaveSize 2
                    it.grunnlag shouldHaveSize 2
                }
            }
        }
    }
}
