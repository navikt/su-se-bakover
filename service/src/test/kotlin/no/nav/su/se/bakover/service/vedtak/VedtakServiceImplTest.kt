package no.nav.su.se.bakover.service.vedtak

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

    @Test
    fun `kopier gjeldende vedtaksdata - fant ikke sak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
        }
        createService(
            sakService = sakServiceMock,
        ).kopierGjeldendeVedtaksdata(
            UUID.randomUUID(),
            LocalDate.EPOCH,
        ) shouldBe KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak.left()
    }

    @Test
    fun `kopier gjeldende vedtaksdata - fant ingen vedtak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn Sak(
                id = UUID.randomUUID(),
                saksnummer = Saksnummer(nummer = 9999),
                opprettet = fixedTidspunkt,
                fnr = Fnr.generer(),
                søknader = listOf(),
                søknadsbehandlinger = listOf(),
                utbetalinger = listOf(),
                revurderinger = listOf(),
                vedtakListe = listOf(),
                type = Sakstype.UFØRE,
            ).right()
        }
        createService(
            sakService = sakServiceMock,
        ).kopierGjeldendeVedtaksdata(
            UUID.randomUUID(),
            LocalDate.EPOCH,
        ) shouldBe KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left()
    }

    @Test
    fun `kopier gjeldende vedtaksdata - ugyldig periode`() {
        val vedtakMock = mock<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling> {
            on { periode } doReturn år(2021)
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn Sak(
                id = UUID.randomUUID(),
                saksnummer = Saksnummer(nummer = 9999),
                opprettet = fixedTidspunkt,
                fnr = Fnr.generer(),
                søknader = listOf(),
                søknadsbehandlinger = listOf(),
                utbetalinger = listOf(),
                revurderinger = listOf(),
                type = Sakstype.UFØRE,
                vedtakListe = listOf(
                    vedtakMock,
                ),
            ).right()
        }
        createService(
            sakService = sakServiceMock,
        ).kopierGjeldendeVedtaksdata(
            UUID.randomUUID(),
            LocalDate.EPOCH.plusDays(7),
        ) shouldBe KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
    }

    @Test
    fun `henter tidligere informasjon for overlappende vedtak`() {

        val sakOgVedtak1 = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            clock = fixedClock,
        )
        val sakOgVedtak2 = vedtakRevurderingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            sakOgVedtakSomKanRevurderes = sakOgVedtak1,
            clock = fixedClock.plus(1, ChronoUnit.DAYS),
        )
        val sakOgVedtak3 = vedtakRevurderingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            sakOgVedtakSomKanRevurderes = sakOgVedtak2,
            clock = fixedClock.plus(2, ChronoUnit.DAYS),
        )
        // TODO jah: Fjern igjen
        sakOgVedtak3.first.vedtakListe shouldBe listOf(sakOgVedtak1.second, sakOgVedtak2.second, sakOgVedtak3.second)

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn listOf(sakOgVedtak1.second, sakOgVedtak2.second, sakOgVedtak3.second)
        }
        createService(
            vedtakRepo = vedtakRepoMock,
        ).historiskGrunnlagForVedtaksperiode(
            UUID.randomUUID(),
            vedtakId = sakOgVedtak2.second.id,
        ) shouldBe GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(sakOgVedtak1.second),
            clock = fixedClock,
        ).right()
    }

    @Test
    fun `henter tidligere informasjon for vedtak`() {
        val sakOgVedtak1 = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            clock = fixedClock,
        )
        val sakOgVedtak2 = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(Periode.create(1.juni(2021), 31.juli(2021))),
            clock = fixedClock.plus(1, ChronoUnit.DAYS),
        )
        val sakOgVedtak3 = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            clock = fixedClock.plus(2, ChronoUnit.DAYS),
        )
        val sakOgVedtak4 = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            clock = fixedClock.plus(3, ChronoUnit.DAYS),
        )

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn listOf(
                sakOgVedtak1.second,
                sakOgVedtak2.second,
                sakOgVedtak3.second,
                sakOgVedtak4.second,
            )
        }
        val actual = createService(
            vedtakRepo = vedtakRepoMock,
        ).historiskGrunnlagForVedtaksperiode(UUID.randomUUID(), vedtakId = sakOgVedtak3.second.id)
            .getOrElse { throw RuntimeException("Test feilet") }

        actual shouldBe GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(sakOgVedtak1.second, sakOgVedtak2.second),
            clock = fixedClock,
        )
    }

    private fun createService(
        vedtakRepo: VedtakRepo = mock(),
        sakService: SakService = mock(),
    ) = VedtakServiceImpl(
        vedtakRepo = vedtakRepo,
        sakService = sakService,
        clock = fixedClock,
    )
}
