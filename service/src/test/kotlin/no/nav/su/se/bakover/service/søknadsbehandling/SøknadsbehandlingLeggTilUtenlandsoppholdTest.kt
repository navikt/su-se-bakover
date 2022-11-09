package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class SøknadsbehandlingLeggTilUtenlandsoppholdTest {
    @Test
    fun `svarer med feil hvis ikke behandling fins`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock { on { hent(any()) } doReturn null },
        ).let {
            val behandlingId = UUID.randomUUID()
            it.søknadsbehandlingService.leggTilUtenlandsopphold(
                LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = behandlingId,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = behandlingId,
                            periode = år(2021),
                            status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()

            verify(it.søknadsbehandlingRepo).hent(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception hvis vilkår har overlappende perioder`() {
        assertThrows<IllegalStateException> {
            SøknadsbehandlingServiceAndMocks(
                søknadsbehandlingRepo = mock { on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().second },
            ).let {
                it.søknadsbehandlingService.leggTilUtenlandsopphold(
                    // I praksis ikke mulig at dette tryner per nå
                    mock {
                        on { behandlingId } doReturn UUID.randomUUID()
                        on { it.tilVilkår(any()) } doReturn LeggTilFlereUtenlandsoppholdRequest.UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    },
                    saksbehandler = saksbehandler,
                )

                verify(it.søknadsbehandlingRepo).hent(any())
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `svarer med feil hvis behandling er i ugyldig tilstand for å legge til opphold i utlandet`() {
        val iverksatt = søknadsbehandlingIverksattInnvilget().second
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock { on { hent(any()) } doReturn iverksatt },
        ).let {
            it.søknadsbehandlingService.leggTilUtenlandsopphold(
                LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = iverksatt.id,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = iverksatt.id,
                            periode = år(2021),
                            status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                fra = iverksatt::class,
                til = Søknadsbehandling.Vilkårsvurdert::class,
            ).left()

            verify(it.søknadsbehandlingRepo).hent(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path`() {
        val innvilget = søknadsbehandlingVilkårsvurdertInnvilget().second
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilget
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.søknadsbehandlingService.leggTilUtenlandsopphold(
                LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = innvilget.id,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = innvilget.id,
                            periode = år(2021),
                            status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            ) shouldBe innvilget.right()

            verify(serviceAndMocks.søknadsbehandlingRepo).hent(any())
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(
                argThat { it shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>() },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.søknadsbehandlingRepo).defaultTransactionContext()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `vilkårene vurderes på nytt når nye utlandsopphold legges til`() {
        val innvilget = søknadsbehandlingVilkårsvurdertInnvilget().second
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilget
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.søknadsbehandlingService.leggTilUtenlandsopphold(
                LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = innvilget.id,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = innvilget.id,
                            periode = år(2021),
                            status = UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            )

            verify(serviceAndMocks.søknadsbehandlingRepo).hent(any())
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(
                argThat { it shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>() },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.søknadsbehandlingRepo).defaultTransactionContext()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }
}
