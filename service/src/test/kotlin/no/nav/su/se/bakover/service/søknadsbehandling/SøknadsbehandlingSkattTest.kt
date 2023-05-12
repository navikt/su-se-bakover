package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.toRange
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.grunnlagsdataMedEpsMedFradrag
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.skatt.nySøknadsbehandlingMedSkattegrunnlag
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Year
import java.util.UUID

class SøknadsbehandlingSkattTest {

    @Test
    fun `henter ny`() {
        val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second
        val skatteServiceMock = mock<SkatteService> {
            on { this.hentSamletSkattegrunnlagForÅr(any(), any(), any()) } doReturn nySkattegrunnlag()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn søknadsbehandling
        }

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            skatteService = skatteServiceMock,
        ).nySkattegrunnlag(søknadsbehandling.id, saksbehandler)

        val expected = nySøknadsbehandlingMedSkattegrunnlag(
            søknadsbehandling = søknadsbehandling,
            søkersId = actual.søkersSkatteId,
        )

        actual shouldBe expected

        verify(skatteServiceMock).hentSamletSkattegrunnlagForÅr(
            argThat { it shouldBe søknadsbehandling.fnr },
            argThat { it shouldBe saksbehandler },
            argThat { it shouldBe Year.of(2020).toRange() },
        )

        verify(søknadsbehandlingRepoMock, times(1)).lagreMedSkattegrunnlag(
            argThat {
                it.søknadsbehandling shouldBe expected.søknadsbehandling
                it.sakId shouldBe expected.søknadsbehandling.sakId
                it.søker shouldBe expected.søker
                it.eps shouldBe null
            },
        )
    }

    @Test
    fun `henter eksisterende (finnes)`() {
        val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second
        val expected = nySøknadsbehandlingMedSkattegrunnlag(
            søknadsbehandling = søknadsbehandling,
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentSkattegrunnlag(any()) } doReturn expected
        }
        createSøknadsbehandlingService(søknadsbehandlingRepo = søknadsbehandlingRepoMock)
            .hentSkattegrunnlag(søknadsbehandling.id) shouldBe expected.right()
        verify(søknadsbehandlingRepoMock).hentSkattegrunnlag(argThat { it shouldBe søknadsbehandling.id })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `henter eksisterende (finnes ikke)`() {
        val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentSkattegrunnlag(any()) } doReturn null
        }
        createSøknadsbehandlingService(søknadsbehandlingRepo = søknadsbehandlingRepoMock)
            .hentSkattegrunnlag(søknadsbehandling.id) shouldBe KunneIkkeHenteSkattemelding.FinnesIkke.left()
        verify(søknadsbehandlingRepoMock).hentSkattegrunnlag(argThat { it shouldBe søknadsbehandling.id })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `exception dersom man prøver å hente ny, ved feil tilstand`() {
        val søknadsbehandling = søknadsbehandlingSimulert().second
        val expected = nySøknadsbehandlingMedSkattegrunnlag(
            søknadsbehandling = søknadsbehandling,
        )
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn søknadsbehandling
            on { hentSkattegrunnlag(any()) } doReturn expected
        }

        assertThrows<IllegalStateException> {
            createSøknadsbehandlingService(søknadsbehandlingRepo = søknadsbehandlingRepoMock)
                .nySkattegrunnlag(søknadsbehandling.id, saksbehandler)
        }
    }

    @Test
    fun `oppfrisker skattegrunnlag`() {
        val søknadsbehandling = søknadsbehandlingSimulert(grunnlagsdata = grunnlagsdataMedEpsMedFradrag()).second
        val medSkattegrunnlag = nySøknadsbehandlingMedSkattegrunnlag(
            søknadsbehandling = søknadsbehandling,
            epsId = UUID.randomUUID(),
            eps = nySkattegrunnlag(),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentSkattegrunnlag(any()) } doReturn medSkattegrunnlag
        }
        val skatteServiceMock = mock<SkatteService> {
            on { this.hentSamletSkattegrunnlagForÅr(any(), any(), any()) } doReturn nySkattegrunnlag()
        }

        createSøknadsbehandlingService(søknadsbehandlingRepo = søknadsbehandlingRepoMock, skatteService = skatteServiceMock)
            .oppfrisk(søknadsbehandling.id, saksbehandler).shouldBeRight()

        verify(søknadsbehandlingRepoMock, times(1)).lagreMedSkattegrunnlag(
            argThat { it shouldBe medSkattegrunnlag },
        )
    }
}
