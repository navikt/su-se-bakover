package no.nav.su.se.bakover.vedtak.application

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.YearMonth
import java.util.UUID

class VedtakServiceFrikortTest {
    @Test
    fun `Vedtak til frikort skal sammenslå saker til en person som har begge ytelser`() {
        val fnr = Fnr.generer()
        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentAlleInnvilgelserOgOpphør() } doReturn listOf(
                VedtaksammendragForSak(
                    fødselsnummer = fnr,
                    sakId = UUID.randomUUID(),
                    saksnummer = Saksnummer(10002001),
                    vedtak = listOf(
                        VedtaksammendragForSak.Vedtak(
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = Periode(YearMonth.of(2025, 1)),
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                            sakstype = Sakstype.UFØRE,
                            epsFnr = emptyList(),
                        ),
                    ),
                ),
                VedtaksammendragForSak(
                    fødselsnummer = fnr,
                    sakId = UUID.randomUUID(),
                    saksnummer = Saksnummer(10002002),
                    vedtak = listOf(
                        VedtaksammendragForSak.Vedtak(
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = Periode(YearMonth.of(2025, 3)),
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                            sakstype = Sakstype.ALDER,
                            epsFnr = emptyList(),
                        ),
                    ),
                ),
            )
        }

        val service = VedtakServiceImpl(
            vedtakRepoMock,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
        )
        val result = service.hentAlleSakerMedInnvilgetVedtak()

        result.saker.size shouldBe 1
        with(result.saker[0]) {
            this.fnr shouldBe fnr.toString()
            vedtak.size shouldBe 2
            vedtak[0].type shouldBe "SØKNADSBEHANDLING_INNVILGELSE"
            vedtak[0].sakstype shouldBe "uføre"
            vedtak[0].fraOgMed shouldBe YearMonth.of(2025, 1).atDay(1)
            vedtak[0].tilOgMed shouldBe YearMonth.of(2025, 1).atEndOfMonth()
            vedtak[1].type shouldBe "SØKNADSBEHANDLING_INNVILGELSE"
            vedtak[1].sakstype shouldBe "alder"
            vedtak[1].fraOgMed shouldBe YearMonth.of(2025, 3).atDay(1)
            vedtak[1].tilOgMed shouldBe YearMonth.of(2025, 3).atEndOfMonth()
        }
    }
}
