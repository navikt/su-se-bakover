package no.nav.su.se.bakover.service.vedtak

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class VedtakServiceImplTest {

    @Test
    fun `kan hente ett fnr`() {
        val dato = 1.mars(2021)
        val fnr = FnrGenerator.random()
        val vedtak = innvilgetVedtak(fnr)

        val vedtakRepoMock = mock<VedtakRepo>() {
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
        val fnr = FnrGenerator.random()
        val vedtak = innvilgetVedtak(fnr)

        val vedtakRepoMock = mock<VedtakRepo>() {
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
        val vedtakFørst = innvilgetVedtak(fnrSist)
        val vedtakSist = innvilgetVedtak(fnrFørst)

        val vedtakRepoMock = mock<VedtakRepo>() {
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
        ).kopierGjeldendeVedtaksdata(UUID.randomUUID(), LocalDate.EPOCH) shouldBeLeft KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak
    }

    @Test
    fun `kopier gjeldende vedtaksdata - fant ingen vedtak`() {
        val sakServiceMock = mock<SakService> {
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
            ).right()
        }
        createService(
            sakService = sakServiceMock,
        ).kopierGjeldendeVedtaksdata(UUID.randomUUID(), LocalDate.EPOCH) shouldBeLeft KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak
    }

    @Test
    fun `kopier gjeldende vedtaksdata - ugyldig periode`() {
        val vedtakMock = mock<Vedtak.EndringIYtelse>() {
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
        }
        val sakServiceMock = mock<SakService> {
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
            ).right()
        }
        createService(
            sakService = sakServiceMock,
        ).kopierGjeldendeVedtaksdata(UUID.randomUUID(), LocalDate.EPOCH.plusDays(7)) shouldBeLeft KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
    }

    private fun createService(
        vedtakRepo: VedtakRepo = mock(),
        sakService: SakService = mock(),
    ) = VedtakServiceImpl(
        vedtakRepo = vedtakRepo,
        sakService = sakService,
        clock = fixedClock,
    )

    private fun innvilgetVedtak(fnr: Fnr) =
        Vedtak.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(2021),
                søknad = Søknad.Journalført.MedOppgave(
                    id = BehandlingTestUtils.søknadId,
                    opprettet = Tidspunkt.EPOCH,
                    sakId = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                    oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                    journalpostId = BehandlingTestUtils.søknadJournalpostId,
                ),
                oppgaveId = oppgaveId,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                fnr = fnr,
                beregning = TestBeregning,
                simulering = mock(),
                saksbehandler = saksbehandler,
                attestering = Attestering.Iverksatt(attestant),
                fritekstTilBrev = "",
                stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
                grunnlagsdata = Grunnlagsdata.EMPTY,
                vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            ),
            UUID30.randomUUID(),
        )

    private val oppgaveId = OppgaveId("2")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saks")
    private val attestant = NavIdentBruker.Attestant("atte")
}
