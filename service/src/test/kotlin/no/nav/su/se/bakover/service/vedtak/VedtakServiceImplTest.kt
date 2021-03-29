package no.nav.su.se.bakover.service.vedtak

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtakServiceImplTest {

    @Test
    fun `kan hente ett fnr`() {
        val dato = 1.mars(2021)
        val fnr = FnrGenerator.random()
        val vedtak = innvilgetVedtak(fnr)

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentAktive(any(), eq(null)) } doReturn listOf(vedtak)
        }

        val actual = createService(
            vedtakRepo = vedtakRepoMock
        ).hentAktiveFnr(dato)

        actual shouldBe listOf(fnr)

        verify(vedtakRepoMock).hentAktive(argThat { it shouldBe dato }, anyOrNull())
        verifyNoMoreInteractions(vedtakRepoMock)
    }

    @Test
    fun `test distinct`() {
        val dato = 1.mars(2021)
        val fnr = FnrGenerator.random()
        val vedtak = innvilgetVedtak(fnr)

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentAktive(any(), eq(null)) } doReturn listOf(vedtak, vedtak)
        }

        val actual = createService(
            vedtakRepo = vedtakRepoMock
        ).hentAktiveFnr(dato)

        actual shouldBe listOf(fnr)

        verify(vedtakRepoMock).hentAktive(argThat { it shouldBe dato }, anyOrNull())
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
            on { hentAktive(any(), eq(null)) } doReturn listOf(vedtakFørst, vedtakSist)
        }

        val actual = createService(
            vedtakRepo = vedtakRepoMock
        ).hentAktiveFnr(dato)

        actual shouldBe listOf(fnrFørst, fnrSist)

        verify(vedtakRepoMock).hentAktive(argThat { it shouldBe dato }, anyOrNull())
        verifyNoMoreInteractions(vedtakRepoMock)
    }

    private fun createService(
        vedtakRepo: VedtakRepo = mock()
    ) = VedtakServiceImpl(
        vedtakRepo = vedtakRepo
    )

    private fun innvilgetVedtak(fnr: Fnr) =
        Vedtak.EndringIYtelse.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1),
                søknad = Søknad.Journalført.MedOppgave(
                    id = BehandlingTestUtils.søknadId,
                    opprettet = Tidspunkt.EPOCH,
                    sakId = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                    oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                    journalpostId = BehandlingTestUtils.søknadJournalpostId
                ),
                oppgaveId = oppgaveId,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                fnr = fnr,
                beregning = TestBeregning,
                simulering = mock(),
                saksbehandler = saksbehandler,
                attestering = Attestering.Iverksatt(attestant),
                fritekstTilBrev = "",
            ),
            UUID30.randomUUID()
        )

    private val oppgaveId = OppgaveId("2")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saks")
    private val attestant = NavIdentBruker.Attestant("atte")
}
