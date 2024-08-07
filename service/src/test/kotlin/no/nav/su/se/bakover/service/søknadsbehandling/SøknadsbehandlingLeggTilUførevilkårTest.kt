package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.UførevilkårStatus
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrad

internal class SøknadsbehandlingLeggTilUførevilkårTest {
    @Test
    fun `happy path`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn nySøknadsbehandlingMedStønadsperiode().second
            },
        ).let { serviceAndMocks ->
            val behandlingId = SøknadsbehandlingId.generer()
            serviceAndMocks.søknadsbehandlingService.leggTilUførevilkår(
                request = LeggTilUførevurderingerRequest(
                    behandlingId = behandlingId,
                    vurderinger = nonEmptyListOf(
                        LeggTilUførevilkårRequest(
                            behandlingId = behandlingId,
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            uføregrad = Uføregrad.parse(50),
                            forventetInntekt = 15000,
                            oppfylt = UførevilkårStatus.VilkårOppfylt,
                            begrunnelse = "jambo",
                        ),
                        LeggTilUførevilkårRequest(
                            behandlingId = behandlingId,
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 30000,
                            oppfylt = UførevilkårStatus.VilkårOppfylt,
                            begrunnelse = "jambo",
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            ).getOrFail().let { medUførevilkår ->
                (medUførevilkår.vilkårsvurderinger.uføreVilkår().getOrFail() as UføreVilkår.Vurdert).let {
                    it.vurderingsperioder shouldHaveSize 2
                    it.grunnlag shouldHaveSize 2
                }
            }
        }
    }
}
