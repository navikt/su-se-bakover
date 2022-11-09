package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class SøknadsbehandlingServiceOppdaterStønadsperiodeTest {

    @Test
    fun `svarer med feil hvis man ikke finner sak`() {
        val (sak, behandling) = søknadsbehandlingVilkårsvurdertUavklart()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
            },
        ).let {
            it.søknadsbehandlingService.oppdaterStønadsperiode(
                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = behandling.id,
                    stønadsperiode = stønadsperiode2021,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeSak.left()

            verify(it.sakService).hentSak(sak.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `oppdaterer stønadsperiode for behandling, grunnlagsdata og vilkårsvurderinger`() {
        val (sak, vilkårsvurdert) = søknadsbehandlingVilkårsvurdertInnvilget()

        val nyStønadsperiode = Stønadsperiode.create(periode = Periode.create(1.juni(2021), 31.mars(2022)))

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            søknadsbehandlingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).let { it ->
            val response = it.søknadsbehandlingService.oppdaterStønadsperiode(
                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = vilkårsvurdert.id,
                    stønadsperiode = nyStønadsperiode,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail()

            vilkårsvurdert.stønadsperiode.periode shouldNotBe nyStønadsperiode

            verify(it.sakService).hentSak(sak.id)
            verify(it.søknadsbehandlingRepo).defaultTransactionContext()
            verify(it.søknadsbehandlingRepo).lagre(
                argThat {
                    it shouldBe response
                    it.stønadsperiode shouldBe nyStønadsperiode
                    it.vilkårsvurderinger.let { vilkårsvurderinger ->
                        vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.all { it.periode == nyStønadsperiode.periode } shouldBe true
                        vilkårsvurderinger.formue.grunnlag.all { it.periode == nyStønadsperiode.periode } shouldBe true
                    }
                },
                anyOrNull(),
            )
            it.verifyNoMoreInteractions()
        }
    }
}
