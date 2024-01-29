package no.nav.su.se.bakover.service.søknadsbehandling

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.StøtterHentingAvEksternGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingSkattCommand
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Year
import java.util.UUID

class VilkårsvurderingerSøknadsbehandlingSkattTest {

    @Test
    fun `henter ny`() {
        val søknadsbehandling = nySøknadsbehandlingMedStønadsperiode().second
        val skatteId = UUID.randomUUID()
        val skatteServiceMock = mock<SkatteService> {
            on { this.hentSamletSkattegrunnlagForÅr(any(), any(), any()) } doReturn nySkattegrunnlag(skatteId)
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn søknadsbehandling
        }

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            skatteService = skatteServiceMock,
        ).oppdaterSkattegrunnlag(
            SøknadsbehandlingSkattCommand(
                søknadsbehandling.id,
                saksbehandler,
                YearRange(Year.of(2019), Year.of(2020)),
            ),
        )

        actual.shouldBeRight()

        verify(skatteServiceMock).hentSamletSkattegrunnlagForÅr(
            argThat { it shouldBe søknadsbehandling.fnr },
            argThat { it shouldBe saksbehandler },
            argThat { it shouldBe YearRange(Year.of(2019), Year.of(2020)) },
        )

        verify(søknadsbehandlingRepoMock).lagre(
            søknadsbehandling = argThat {
                it shouldBe søknadsbehandling.copy(
                    grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.copy(
                        eksterneGrunnlag = StøtterHentingAvEksternGrunnlag(
                            skatt = EksterneGrunnlagSkatt.Hentet(søkers = nySkattegrunnlag(skatteId), eps = null),
                        ),
                    ),
                )
            },
            sessionContext = anyOrNull(),
        )
    }

    @Test
    fun `lagrer ikke dersom left`() {
        val søknadsbehandling = tilAttesteringSøknadsbehandling().second
        val skatteServiceMock = mock<SkatteService> {
            on { this.hentSamletSkattegrunnlagForÅr(any(), any(), any()) } doReturn nySkattegrunnlag()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn søknadsbehandling
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            skatteService = skatteServiceMock,
        ).oppdaterSkattegrunnlag(
            SøknadsbehandlingSkattCommand(søknadsbehandling.id, saksbehandler, YearRange(Year.of(2019), Year.of(2020))),
        ).shouldBeLeft()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe søknadsbehandling.id })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
