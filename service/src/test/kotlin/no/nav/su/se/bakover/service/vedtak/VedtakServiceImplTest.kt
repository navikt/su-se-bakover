package no.nav.su.se.bakover.service.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class VedtakServiceImplTest {

    @Test
    fun `kan hente ett fnr`() {
        val dato = 1.mars(2021)
        val fnr = Fnr.generer()
        val vedtak = iverksattSøknadsbehandlingUføre(
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(5555),
                fnr = fnr,
                type = Sakstype.UFØRE,
            ),
        ).third as VedtakSomKanRevurderes.EndringIYtelse

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentAktive(any()) } doReturn listOf(vedtak)
        }

        val actual = createService(
            vedtakRepo = vedtakRepoMock,
        ).hentAktiveFnr(dato)

        actual shouldBe listOf(fnr)

        verify(vedtakRepoMock).hentAktive(argThat { it shouldBe dato })
        verifyNoMoreInteractions(vedtakRepoMock)
    }

    @Test
    fun `test distinct`() {
        val dato = 1.mars(2021)
        val fnr = Fnr.generer()
        val vedtak = iverksattSøknadsbehandlingUføre(
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(5555),
                fnr = fnr,
                type = Sakstype.UFØRE,
            ),
        ).third as VedtakSomKanRevurderes.EndringIYtelse

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentAktive(any()) } doReturn listOf(vedtak, vedtak)
        }

        val actual = createService(
            vedtakRepo = vedtakRepoMock,
        ).hentAktiveFnr(dato)

        actual shouldBe listOf(fnr)

        verify(vedtakRepoMock).hentAktive(argThat { it shouldBe dato })
        verifyNoMoreInteractions(vedtakRepoMock)
    }

    @Test
    fun `test sort`() {
        val dato = 1.mars(2021)
        val fnrFørst = Fnr("01010112345")
        val fnrSist = Fnr("01010212345")
        val vedtakFørst = iverksattSøknadsbehandlingUføre(
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(5555),
                fnr = fnrFørst,
                type = Sakstype.UFØRE,
            ),
        ).third as VedtakSomKanRevurderes.EndringIYtelse

        val vedtakSist = iverksattSøknadsbehandlingUføre(
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(6666),
                fnr = fnrSist,
                type = Sakstype.UFØRE,
            ),
        ).third as VedtakSomKanRevurderes.EndringIYtelse

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentAktive(any()) } doReturn listOf(vedtakFørst, vedtakSist)
        }

        val actual = createService(
            vedtakRepo = vedtakRepoMock,
        ).hentAktiveFnr(dato)

        actual shouldBe listOf(fnrFørst, fnrSist)

        verify(vedtakRepoMock).hentAktive(argThat { it shouldBe dato })
        verifyNoMoreInteractions(vedtakRepoMock)
    }

    private fun createService(
        vedtakRepo: VedtakRepo = mock(),
    ) = VedtakServiceImpl(
        vedtakRepo = vedtakRepo,
        clock = fixedClock,
    )
}
