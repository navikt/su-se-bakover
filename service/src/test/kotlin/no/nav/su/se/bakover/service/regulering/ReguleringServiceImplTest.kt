package no.nav.su.se.bakover.service.regulering

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.VedtakSomKanReguleres
import no.nav.su.se.bakover.domain.regulering.VedtakType
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDateTime
import java.util.UUID

internal class ReguleringServiceImplTest {
    @Test
    fun `En sak som er stanset må g-reguleres manuellt`() {
        val reguleringRepoMock: ReguleringRepo = mock {
            on { hentVedtakSomKanReguleres(any()) } doReturn listOf(
                lagSak(
                    VedtakType.STANS_AV_YTELSE,
                    ReguleringType.AUTOMATISK,
                ),
            )
        }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepoMock,
            sakRepo = mock(),
            utbetalingService = mock(),
            vedtakService = mock(),
            vilkårsvurderingService = mock(),
            grunnlagService = mock(),
            clock = fixedClock,
        )

        reguleringService.hentAlleSakerSomKanReguleres(1.mai(2021))
            .saker
            .first()
            .reguleringType shouldBe ReguleringType.MANUELL
    }

    @Test
    fun `Vedtak for avvist klage og avslag tas ikke med`() {
        val reguleringRepoMock: ReguleringRepo = mock {
            on { hentVedtakSomKanReguleres(any()) } doReturn listOf(
                lagSak(VedtakType.AVVIST_KLAGE, ReguleringType.AUTOMATISK, rekkefølge = 1),
                lagSak(VedtakType.AVSLAG, ReguleringType.AUTOMATISK, rekkefølge = 2),
            )
        }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepoMock,
            sakRepo = mock(),
            utbetalingService = mock(),
            vedtakService = mock(),
            vilkårsvurderingService = mock(),
            grunnlagService = mock(),
            clock = fixedClock,
        )

        reguleringService.hentAlleSakerSomKanReguleres(1.mai(2021))
            .saker
            .shouldBeEmpty()
    }

    @Test
    fun `Vedtak med opphør innen første mai skal ikke reguleres`() {
        val sakId = UUID.randomUUID()
        val reguleringRepoMock: ReguleringRepo = mock {
            on { hentVedtakSomKanReguleres(any()) } doReturn listOf(
                lagSak(VedtakType.SØKNAD, ReguleringType.AUTOMATISK, sakId = sakId, rekkefølge = 1),
                lagSak(
                    VedtakType.OPPHØR,
                    ReguleringType.AUTOMATISK,
                    sakId = sakId,
                    Periode.create(1.mai(2021), 31.desember(2021)),
                    rekkefølge = 2,
                ),
            )
        }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepoMock,
            sakRepo = mock(),
            utbetalingService = mock(),
            vedtakService = mock(),
            vilkårsvurderingService = mock(),
            grunnlagService = mock(),
            clock = fixedClock,
        )

        reguleringService.hentAlleSakerSomKanReguleres(1.mai(2021))
            .saker
            .shouldBeEmpty()
    }

    private fun lagSak(
        vedtakType: VedtakType,
        reguleringType: ReguleringType,
        sakId: UUID = UUID.randomUUID(),
        periode: Periode = stønadsperiode2021.periode,
        rekkefølge: Long = 0,
    ): VedtakSomKanReguleres =
        VedtakSomKanReguleres(
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            opprettet = LocalDateTime.now().plusDays(rekkefølge).toTidspunkt(zoneIdOslo),
            behandlingId = UUID.randomUUID(),
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            vedtakType = vedtakType,
            reguleringType = reguleringType,
        )
}
