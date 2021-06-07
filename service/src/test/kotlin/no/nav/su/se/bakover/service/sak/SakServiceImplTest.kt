package no.nav.su.se.bakover.service.sak

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

internal class SakServiceImplTest {
    @Test
    fun `Oppretter sak og publiserer event`() {
        val sakId = UUID.randomUUID()
        val sak: Sak = mock() {
            on { id } doReturn sakId
        }
        val sakRepo: SakRepo = mock {
            on { opprettSak(any()) }.doNothing()
            on { hentSak(any<UUID>()) } doReturn sak
        }

        val observer: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val sakService = SakServiceImpl(sakRepo)
        sakService.observers.add(observer)
        sakService.opprettSak(mock { on { id } doReturn sakId })

        verify(sakRepo).opprettSak(any())
        verify(sakRepo).hentSak(sak.id)
        verify(observer).handle(argThat { it shouldBe Event.Statistikk.SakOpprettet(sak) })
    }

    @Test
    fun `Publiserer ikke event ved feil av opprettelse av sak`() {
        val sakRepo: SakRepo = mock {
            on { opprettSak(any()) } doThrow RuntimeException("hehe exception")
        }

        val observer: EventObserver = mock()

        val sakService = SakServiceImpl(sakRepo)
        sakService.observers.add(observer)
        assertThrows<RuntimeException> {
            sakService.opprettSak(mock())
            verify(sakRepo).opprettSak(any())
            verifyNoMoreInteractions(sakRepo)
            verifyZeroInteractions(observer)
        }
    }

    @Test
    fun `kopier gjeldende vedtaksdata - fant ikke sak`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn null
        }
        SakServiceImpl(sakRepoMock).kopierGjeldendeVedtaksdata(UUID.randomUUID(), LocalDate.EPOCH) shouldBeLeft KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak
    }

    @Test
    fun `kopier gjeldende vedtaksdata - fant ingen vedtak`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn Sak(
                id = UUID.randomUUID(),
                saksnummer = Saksnummer(nummer = 9999),
                opprettet = Tidspunkt.now(),
                fnr = FnrGenerator.random(),
                søknader = listOf(),
                behandlinger = listOf(),
                utbetalinger = listOf(),
                revurderinger = listOf(),
                vedtakListe = listOf(),
            )
        }
        SakServiceImpl(sakRepoMock).kopierGjeldendeVedtaksdata(UUID.randomUUID(), LocalDate.EPOCH) shouldBeLeft KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak
    }

    @Test
    fun `kopier gjeldende vedtaksdata - ugyldig periode`() {
        val vedtakMock = mock<Vedtak.EndringIYtelse>() {
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
        }
        val sakRepoMock = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn Sak(
                id = UUID.randomUUID(),
                saksnummer = Saksnummer(nummer = 9999),
                opprettet = Tidspunkt.now(),
                fnr = FnrGenerator.random(),
                søknader = listOf(),
                behandlinger = listOf(),
                utbetalinger = listOf(),
                revurderinger = listOf(),
                vedtakListe = listOf(
                    vedtakMock,
                ),
            )
        }
        SakServiceImpl(sakRepoMock).kopierGjeldendeVedtaksdata(UUID.randomUUID(), LocalDate.EPOCH.plusDays(7)) shouldBeLeft KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
    }
}
