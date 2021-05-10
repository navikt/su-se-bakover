package no.nav.su.se.bakover.service.vedtak

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest.AvslagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.util.UUID

internal class FerdigstillVedtakServiceImplTest {

    @Test
    fun `prøver ikke ferdigstille dersom kvittering er feil`() {
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.FEIL, "")
            on { id } doReturn utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.OPPHØR
        }
        val mocks = FerdigstillVedtakServiceMocks()
        mocks.ferdigstillVedtakService.ferdigstillVedtakEtterUtbetaling(utbetalingMock)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er gjennoppta`() {
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.GJENOPPTA
        }
        val mocks = FerdigstillVedtakServiceMocks()
        mocks.ferdigstillVedtakService.ferdigstillVedtakEtterUtbetaling(utbetalingMock)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er stans`() {
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.STANS
        }
        val mocks = FerdigstillVedtakServiceMocks()
        mocks.ferdigstillVedtakService.ferdigstillVedtakEtterUtbetaling(utbetalingMock)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ferdigstill NY kaster feil hvis man ikke finner person for journalpost`() {
        val vedtak = innvilgetVedtak()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForUtbetaling(any()) } doReturn vedtak
        }

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn vedtak.utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.NY
        }

        assertThrows<FerdigstillVedtakServiceImpl.KunneIkkeFerdigstilleVedtakException> {
            createService(
                vedtakRepo = vedtakRepoMock,
                personService = personServiceMock,
            ).ferdigstillVedtakEtterUtbetaling(utbetalingMock)
        }
        inOrder(
            vedtakRepoMock,
            personServiceMock,
        ) {
            verify(vedtakRepoMock).hentForUtbetaling(vedtak.utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
        }
    }

    @Test
    fun `ferdigstillelse etter utbetaling kaster feil hvis journalføring feiler`() {
        val vedtak = innvilgetVedtak()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn graphApiResponse.displayName.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForUtbetaling(any()) } doReturn vedtak
        }

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn vedtak.utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.OPPHØR
        }

        assertThrows<FerdigstillVedtakServiceImpl.KunneIkkeFerdigstilleVedtakException> {
            createService(
                vedtakRepo = vedtakRepoMock,
                personService = personServiceMock,
                microsoftGraphApiClient = microsoftGraphApiOppslagMock,
                brevService = brevServiceMock,
            ).ferdigstillVedtakEtterUtbetaling(utbetalingMock)
        }
        inOrder(
            vedtakRepoMock,
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
        ) {
            verify(vedtakRepoMock).hentForUtbetaling(vedtak.utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(vedtak.behandling.fnr)
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verify(brevServiceMock).journalførBrev(any(), any())
        }
    }

    @Test
    fun `ferdigstillelse etter utbetaling kaster feil hvis distribusjon feiler`() {
        val vedtak = innvilgetVedtak()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn graphApiResponse.displayName.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForUtbetaling(any()) } doReturn vedtak
        }

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn vedtak.utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.OPPHØR
        }

        assertThrows<FerdigstillVedtakServiceImpl.KunneIkkeFerdigstilleVedtakException> {
            createService(
                vedtakRepo = vedtakRepoMock,
                personService = personServiceMock,
                microsoftGraphApiClient = microsoftGraphApiOppslagMock,
                brevService = brevServiceMock,
            ).ferdigstillVedtakEtterUtbetaling(utbetalingMock)
        }
        inOrder(
            vedtakRepoMock,
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
        ) {
            verify(vedtakRepoMock).hentForUtbetaling(vedtak.utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(vedtak.behandling.fnr)
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verify(brevServiceMock).journalførBrev(any(), any())
            verify(brevServiceMock).distribuerBrev(any())
        }
    }

    @Test
    fun `ferdigstill NY etter utbetaling hopper over journalføring dersom det allerede er utført`() {
        val vedtak = journalførtInnvilgetVedtak()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn graphApiResponse.displayName.right()
        }

        val brevServiceMock = mock<BrevService>() {
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForUtbetaling(any()) } doReturn vedtak
        }

        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn vedtak.utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.NY
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        createService(
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).ferdigstillVedtakEtterUtbetaling(utbetalingMock)

        inOrder(
            vedtakRepoMock,
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(vedtakRepoMock).hentForUtbetaling(vedtak.utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(vedtak.behandling.fnr)
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verify(brevServiceMock, never()).journalførBrev(any(), any())
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(vedtak.behandling.oppgaveId)
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
        }
    }

    @Test
    fun `ferdigstill OPPHØR etter utbetaling hopper over journalføring og distribusjon dersom det allerede er utført`() {
        val vedtak = journalførtOgDistribuertInnvilgetVedtak()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn graphApiResponse.displayName.right()
        }

        val brevServiceMock = mock<BrevService>()

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForUtbetaling(any()) } doReturn vedtak
        }

        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn vedtak.utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.OPPHØR
        }

        createService(
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).ferdigstillVedtakEtterUtbetaling(utbetalingMock)

        inOrder(
            vedtakRepoMock,
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(vedtakRepoMock).hentForUtbetaling(vedtak.utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(vedtak.behandling.fnr)
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verify(brevServiceMock, never()).journalførBrev(any(), any())
            verify(brevServiceMock, never()).distribuerBrev(any())
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(vedtak.behandling.oppgaveId)
            verify(vedtakRepoMock, never()).lagre(any())
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            verifyNoMoreInteractions(behandlingMetricsMock)
        }
    }

    @Test
    fun `ferdigstill NY etter utbetaling går fint`() {
        val vedtak = innvilgetVedtak()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturnConsecutively listOf(
                saksbehandler.navIdent.right(),
                attestant.navIdent.right(),
            )
        }

        val brevServiceMock = mock<BrevService>() {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForUtbetaling(any()) } doReturn vedtak
        }

        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn vedtak.utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.NY
        }

        createService(
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).ferdigstillVedtakEtterUtbetaling(utbetalingMock)

        inOrder(
            vedtakRepoMock,
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(vedtakRepoMock).hentForUtbetaling(vedtak.utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(vedtak.behandling.fnr)
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verify(brevServiceMock).journalførBrev(
                argThat {
                    LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = vedtak.beregning,
                        behandlingsinformasjon = vedtak.behandlingsinformasjon,
                        saksbehandlerNavn = vedtak.saksbehandler.navIdent,
                        attestantNavn = vedtak.saksbehandler.navIdent,
                        fritekst = "",
                    )
                },
                argThat { vedtak.behandling.saksnummer },
            )
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(vedtak.behandling.oppgaveId)
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
        }
    }

    @Test
    fun `ferdigstill NY av regulering av grunnbeløp etter utbetaling skal ikke sende brev men skal sende oppgave`() {
        val vedtak = innvilgetRevurdertVedtak()

        val personServiceMock = mock<PersonService>()

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag>()

        val brevServiceMock = mock<BrevService>()

        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForUtbetaling(any()) } doReturn vedtak
        }

        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
            on { id } doReturn vedtak.utbetalingId
            on { type } doReturn Utbetaling.UtbetalingsType.NY
        }

        createService(
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).ferdigstillVedtakEtterUtbetaling(utbetalingMock)

        inOrder(
            vedtakRepoMock,
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(vedtakRepoMock).hentForUtbetaling(vedtak.utbetalingId)
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(vedtak.behandling.oppgaveId)
        }
        verifyNoMoreInteractions(
            vedtakRepoMock,
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `svarer med feil hvis man ikke finner saksbehandler for journalpost`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant.left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock).hentNavnForNavIdent(argThat { it shouldBe vedtak.saksbehandler })
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner person for journalpost`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkePerson.left()

        inOrder(personServiceMock) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner attestant for journalpost`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturnConsecutively listOf(
                graphApiResponse.displayName.right(),
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left(),
            )
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant.left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
        }
    }

    @Test
    fun `sender ikke brev for revurdering ingen endring som ikke skal føre til brevutsending`() {
        val vedtak = innvilgetVedtak().let {
            it.copy(
                behandling = IverksattRevurdering.IngenEndring(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                        .withAlleVilkårOppfylt(),
                    beregning = TestBeregning,
                    saksbehandler = saksbehandler,
                    attestering = Attestering.Iverksatt(attestant),
                    fritekstTilBrev = "",
                    periode = it.periode,
                    tilRevurdering = it,
                    revurderingsårsak = Revurderingsårsak(
                        Revurderingsårsak.Årsak.ANDRE_KILDER,
                        Revurderingsårsak.Begrunnelse.create("begrunnelse"),
                    ),
                    skalFøreTilBrevutsending = false,
                    forhåndsvarsel = null,
                    grunnlagsdata = Grunnlagsdata.EMPTY,
                    vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                    informasjonSomRevurderes = emptyMap(),
                ),
            )
        }

        val mocks = FerdigstillVedtakServiceMocks()
        mocks.ferdigstillVedtakService.journalførOgLagre(vedtak) shouldBe vedtak.right()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom journalføring av brev feiler`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn graphApiResponse.displayName.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock,
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FeilVedJournalføring.left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verify(brevServiceMock).journalførBrev(any(), any())
        }
    }

    @Test
    fun `svarer med feil dersom journalføring av brev allerede er utført`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn graphApiResponse.displayName.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>()
        val brevServiceMock = mock<BrevService>()

        val vedtak = journalførtAvslagsVedtak()
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.AlleredeJournalført(
            iverksattJournalpostId,
        ).left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
            vedtakRepoMock,
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verifyZeroInteractions(vedtakRepoMock, brevServiceMock, behandlingMetricsMock)
        }
    }

    @Test
    fun `oppdaterer vedtak og lagrer dersom journalføring går fint`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturnConsecutively listOf(
                saksbehandler.navIdent.right(),
                attestant.navIdent.right(),
            )
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>()

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock,
            vedtakRepo = vedtakRepoMock,
            behandlingMetrics = behandlingMetricsMock,
        ).journalførOgLagre(vedtak)

        response shouldBe vedtak.copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                iverksattJournalpostId,
            ),
        ).right()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
            vedtakRepoMock,
            behandlingMetricsMock,
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentNavnForNavIdent(any())
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            Tidspunkt.now(fixedClock),
                            avslagsgrunner = vedtak.avslagsgrunner,
                            harEktefelle = false,
                            beregning = vedtak.beregning,
                        ),
                        saksbehandlerNavn = vedtak.saksbehandler.navIdent,
                        attestantNavn = vedtak.attestant.navIdent,
                        fritekst = "",
                    )
                },
                argThat { it shouldBe vedtak.behandling.saksnummer },
            )
            verify(vedtakRepoMock).lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                        iverksattJournalpostId,
                    ),
                ),
            )
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
        }
    }

    @Test
    fun `svarer med feil dersom brevdistribusjon feiler`() {
        val brevServiceMock = mock<BrevService> {
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val vedtak = journalførtAvslagsVedtak()

        val response = createService(
            brevService = brevServiceMock,
        ).distribuerOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.FeilVedDistribusjon(
            iverksattJournalpostId,
        ).left()

        inOrder(brevServiceMock) {
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
        }
    }

    @Test
    fun `svarer med feil dersom distribusjon ikke er journalført først`() {
        val vedtakRepoMock = mock<VedtakRepo>()
        val brevServiceMock = mock<BrevService>()

        val vedtak = avslagsVedtak()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
        ).distribuerOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.MåJournalføresFørst.left()

        inOrder(
            vedtakRepoMock,
            brevServiceMock,
        ) {
            verifyZeroInteractions(vedtakRepoMock, brevServiceMock)
        }
    }

    @Test
    fun `svarer med feil dersom distribusjon av brev allerede er utført`() {
        val vedtakRepoMock = mock<VedtakRepo>()
        val brevServiceMock = mock<BrevService>()

        val vedtak = journalførtOgDistribuertAvslagsVedtak()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
        ).distribuerOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.AlleredeDistribuert(
            iverksattJournalpostId,
        ).left()

        inOrder(
            vedtakRepoMock,
            brevServiceMock,
        ) {
            verifyZeroInteractions(vedtakRepoMock, brevServiceMock)
        }
    }

    @Test
    fun `oppdaterer vedtak og lagrer dersom brevdistribusjon går fint`() {
        val brevServiceMock = mock<BrevService> {
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>()

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val vedtak = journalførtAvslagsVedtak()

        val response = createService(
            brevService = brevServiceMock,
            vedtakRepo = vedtakRepoMock,
            behandlingMetrics = behandlingMetricsMock,
        ).distribuerOgLagre(vedtak)

        response shouldBe vedtak.copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                iverksattJournalpostId,
                iverksattBrevbestillingId,
            ),
        ).right()

        inOrder(
            brevServiceMock,
            vedtakRepoMock,
            behandlingMetricsMock,
        ) {
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
            verify(vedtakRepoMock).lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                        iverksattJournalpostId,
                        iverksattBrevbestillingId,
                    ),
                ),
            )
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
        }
    }

    @Test
    fun `svarer med feil dersom lukking av oppgave feiler`() {
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val vedtak = journalførtOgDistribuertAvslagsVedtak()

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).lukkOppgaveMedBruker(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave.left()

        inOrder(
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verifyZeroInteractions(behandlingMetricsMock)
        }
    }

    @Test
    fun `opprettelse av manglende journalpost og brevbestilling gjør ingenting hvis ingenting mangler`() {
        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentUtenJournalpost() } doReturn emptyList()
            on { hentUtenBrevbestilling() } doReturn emptyList()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            behandlingMetrics = behandlingMetricsMock,
        ).opprettManglendeJournalposterOgBrevbestillinger()

        response shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = emptyList(),
        )

        inOrder(
            vedtakRepoMock,
        ) {
            verify(vedtakRepoMock).hentUtenJournalpost()
            verify(vedtakRepoMock).hentUtenBrevbestilling()
        }
        verifyNoMoreInteractions(vedtakRepoMock, behandlingMetricsMock)
    }

    @Test
    fun `opprettelse av manglende journalpost feiler teknisk`() {
        val vedtak = avslagsVedtak()

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentUtenJournalpost() } doReturn listOf(vedtak)
            on { hentUtenBrevbestilling() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturnConsecutively listOf(
                "saksa".right(),
                "atta".right(),
            )
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
        ).opprettManglendeJournalposterOgBrevbestillinger()

        response shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                FerdigstillVedtakService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = vedtak.behandling.sakId,
                    behandlingId = vedtak.behandling.id,
                    grunn = "FeilVedJournalføring",
                ).left(),
            ),
            brevbestillingsresultat = emptyList(),
        )

        inOrder(
            vedtakRepoMock,
            brevServiceMock,
        ) {
            verify(vedtakRepoMock).hentUtenJournalpost()
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            Tidspunkt.now(fixedClock),
                            avslagsgrunner = vedtak.avslagsgrunner,
                            harEktefelle = false,
                            beregning = vedtak.beregning,
                        ),
                        saksbehandlerNavn = "saksa",
                        attestantNavn = "atta",
                        fritekst = "",
                    )
                },
                argThat { it shouldBe vedtak.behandling.saksnummer },
            )
            verify(vedtakRepoMock).hentUtenBrevbestilling()
        }
        verifyNoMoreInteractions(vedtakRepoMock, brevServiceMock)
    }

    @Test
    fun `oppretter manglende jornalpost for vedtak`() {
        val avslagsVedtak = avslagsVedtak()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn graphApiResponse.displayName.right()
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentUtenJournalpost() } doReturn listOf(avslagsVedtak)
            on { hentUtenBrevbestilling() } doReturn emptyList()
        }

        val brevServiceMock = mock<BrevService>() {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            behandlingMetrics = behandlingMetricsMock,
        ).opprettManglendeJournalposterOgBrevbestillinger()

        response shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                FerdigstillVedtakService.OpprettetJournalpostForIverksetting(
                    sakId = avslagsVedtak.behandling.sakId,
                    behandlingId = avslagsVedtak.behandling.id,
                    journalpostId = iverksattJournalpostId,
                ).right(),
            ),
            brevbestillingsresultat = emptyList(),
        )

        inOrder(
            vedtakRepoMock,
            brevServiceMock,
            behandlingMetricsMock,
        ) {
            verify(vedtakRepoMock).hentUtenJournalpost()
            verify(brevServiceMock).journalførBrev(any(), any())
            verify(vedtakRepoMock).lagre(
                argThat {
                    it shouldBe avslagsVedtak.copy(
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                            iverksattJournalpostId,
                        ),
                    )
                },
            )
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
            verify(vedtakRepoMock).hentUtenBrevbestilling()
        }
    }

    @Test
    fun `oppretter manglende brevbestilling for journalført vedtak`() {
        val innvilgelseUtenBrevbestilling = journalførtInnvilgetVedtak()
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentUtenJournalpost() } doReturn emptyList()
            on { hentUtenBrevbestilling() } doReturn listOf(innvilgelseUtenBrevbestilling)
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId) } doReturn utbetalingMock
        }

        val brevServiceMock = mock<BrevService>() {
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            utbetalingRepo = utbetalingRepoMock,
            brevService = brevServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).opprettManglendeJournalposterOgBrevbestillinger()

        response shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = listOf(
                FerdigstillVedtakService.BestiltBrev(
                    sakId = innvilgelseUtenBrevbestilling.behandling.sakId,
                    behandlingId = innvilgelseUtenBrevbestilling.behandling.id,
                    journalpostId = iverksattJournalpostId,
                    brevbestillingId = iverksattBrevbestillingId,
                ).right(),
            ),
        )

        inOrder(
            vedtakRepoMock,
            brevServiceMock,
            utbetalingRepoMock,
            behandlingMetricsMock,
        ) {
            verify(vedtakRepoMock).hentUtenJournalpost()
            verify(vedtakRepoMock).hentUtenBrevbestilling()
            verify(utbetalingRepoMock).hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId)
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
            verify(vedtakRepoMock).lagre(
                argThat {
                    it shouldBe innvilgelseUtenBrevbestilling.copy(
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                            iverksattJournalpostId,
                            iverksattBrevbestillingId,
                        ),
                    )
                },
            )
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
            verifyNoMoreInteractions(vedtakRepoMock, brevServiceMock, utbetalingRepoMock)
        }
    }

    @Test
    fun `kan ikke opprette manglende brevbestilling hvis vedtak ikke er journalført`() {
        val innvilgelseUtenBrevbestilling = innvilgetVedtak()
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.OK, "")
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentUtenJournalpost() } doReturn emptyList()
            on { hentUtenBrevbestilling() } doReturn listOf(innvilgelseUtenBrevbestilling)
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId) } doReturn utbetalingMock
        }

        val brevServiceMock = mock<BrevService>()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            utbetalingRepo = utbetalingRepoMock,
        ).opprettManglendeJournalposterOgBrevbestillinger()

        response shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = listOf(
                FerdigstillVedtakService.KunneIkkeBestilleBrev(
                    sakId = innvilgelseUtenBrevbestilling.behandling.sakId,
                    behandlingId = innvilgelseUtenBrevbestilling.behandling.id,
                    journalpostId = null,
                    grunn = "MåJournalføresFørst",
                ).left(),
            ),
        )

        inOrder(
            vedtakRepoMock,
            utbetalingRepoMock,
        ) {
            verify(vedtakRepoMock).hentUtenJournalpost()
            verify(vedtakRepoMock).hentUtenBrevbestilling()
            verify(utbetalingRepoMock).hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId)
        }
        verifyNoMoreInteractions(vedtakRepoMock, brevServiceMock)
    }

    @Test
    fun `oppretter ikke manglende journalpost for vedtak med ukvitterte utbetalinger eller kvitteringer med feil`() {
        val innvilgetVedtakUkvittertUtbetaling = innvilgetVedtak()
        val ukvittertUtbetaling = mock<Utbetaling.OversendtUtbetaling.UtenKvittering>()

        val innvilgetVedtakKvitteringMedFeil = innvilgetVedtak()
        val kvitteringMedFeil = mock<Utbetaling.OversendtUtbetaling.MedKvittering>() {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.FEIL, "")
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentUtenJournalpost() } doReturn emptyList()
            on { hentUtenBrevbestilling() } doReturn listOf(
                innvilgetVedtakUkvittertUtbetaling,
                innvilgetVedtakKvitteringMedFeil,
            )
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(innvilgetVedtakUkvittertUtbetaling.utbetalingId) } doReturn ukvittertUtbetaling
            on { hentUtbetaling(innvilgetVedtakKvitteringMedFeil.utbetalingId) } doReturn kvitteringMedFeil
        }

        val brevServiceMock = mock<BrevService>()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            utbetalingRepo = utbetalingRepoMock,
            brevService = brevServiceMock,
        ).opprettManglendeJournalposterOgBrevbestillinger()

        response shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = emptyList(),
        )

        verify(vedtakRepoMock).hentUtenJournalpost()
        verify(vedtakRepoMock).hentUtenBrevbestilling()
        verify(utbetalingRepoMock).hentUtbetaling(innvilgetVedtakUkvittertUtbetaling.utbetalingId)
        verify(utbetalingRepoMock).hentUtbetaling(innvilgetVedtakKvitteringMedFeil.utbetalingId)
        verifyNoMoreInteractions(vedtakRepoMock, brevServiceMock, utbetalingRepoMock)
    }

    private fun createService(
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        clock: Clock = fixedClock,
        microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
        vedtakRepo: VedtakRepo = mock(),
        utbetalingRepo: UtbetalingRepo = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
    ) = FerdigstillVedtakServiceImpl(
        oppgaveService = oppgaveService,
        personService = personService,
        clock = clock,
        microsoftGraphApiOppslag = microsoftGraphApiClient,
        brevService = brevService,
        vedtakRepo = vedtakRepo,
        utbetalingRepo = utbetalingRepo,
        behandlingMetrics = behandlingMetrics,
    )

    internal data class FerdigstillVedtakServiceMocks(
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val clock: Clock = fixedClock,
        val microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
        val brevService: BrevService = mock(),
        val vedtakRepo: VedtakRepo = mock(),
        val utbetalingRepo: UtbetalingRepo = mock(),
        val behandlingMetrics: BehandlingMetrics = mock(),
    ) {
        val ferdigstillVedtakService = FerdigstillVedtakServiceImpl(
            oppgaveService = oppgaveService,
            personService = personService,
            clock = clock,
            microsoftGraphApiOppslag = microsoftGraphApiClient,
            brevService = brevService,
            vedtakRepo = vedtakRepo,
            utbetalingRepo = utbetalingRepo,
            behandlingMetrics = behandlingMetrics,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(
                oppgaveService,
                personService,
                microsoftGraphApiClient,
                brevService,
                vedtakRepo,
                utbetalingRepo,
                behandlingMetrics,
            )
        }
    }

    private fun avslagsVedtak() =
        Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(
            Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
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
                fnr = FnrGenerator.random(),
                beregning = TestBeregning,
                attestering = Attestering.Iverksatt(attestant),
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
                stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
                grunnlagsdata = Grunnlagsdata.EMPTY,
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            ),
        )

    private fun journalførtAvslagsVedtak() =
        avslagsVedtak().copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                iverksattJournalpostId,
            ),
        )

    private fun journalførtOgDistribuertAvslagsVedtak() =
        avslagsVedtak().copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                iverksattJournalpostId,
                iverksattBrevbestillingId,
            ),
        )

    private fun createSøknad() = Søknad.Journalført.MedOppgave(
        id = BehandlingTestUtils.søknadId,
        opprettet = Tidspunkt.EPOCH,
        sakId = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        oppgaveId = BehandlingTestUtils.søknadOppgaveId,
        journalpostId = BehandlingTestUtils.søknadJournalpostId,
    )

    private fun innvilgetVedtak(): Vedtak.EndringIYtelse {

        return Vedtak.fromSøknadsbehandling(
            søknadsbehandling = Søknadsbehandling.Iverksatt.Innvilget(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(2021),
                søknad = createSøknad(),
                oppgaveId = oppgaveId,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                fnr = FnrGenerator.random(),
                beregning = TestBeregning,
                simulering = mock(),
                saksbehandler = saksbehandler,
                attestering = Attestering.Iverksatt(attestant),
                fritekstTilBrev = "",
                stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
                grunnlagsdata = Grunnlagsdata.EMPTY,
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            ),
            utbetalingId = UUID30.randomUUID(),
        )
    }

    private fun innvilgetRevurdertVedtak() =
        Vedtak.from(
            revurdering = IverksattRevurdering.Innvilget(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                oppgaveId = oppgaveId,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                beregning = TestBeregning,
                simulering = mock(),
                saksbehandler = saksbehandler,
                attestering = Attestering.Iverksatt(attestant),
                fritekstTilBrev = "",
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                tilRevurdering = innvilgetVedtak(),
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                    Revurderingsårsak.Begrunnelse.create("regulert grunnbeløp"),
                ),
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                grunnlagsdata = Grunnlagsdata.EMPTY,
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                informasjonSomRevurderes = emptyMap(),
            ),
            utbetalingId = UUID30.randomUUID(),
        )

    private fun journalførtInnvilgetVedtak() =
        innvilgetVedtak().copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                iverksattJournalpostId,
            ),
        )

    private fun journalførtOgDistribuertInnvilgetVedtak() =
        innvilgetVedtak().copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                iverksattJournalpostId,
                iverksattBrevbestillingId,
            ),
        )

    private val person = Person(
        ident = Ident(
            fnr = BehandlingTestUtils.fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )
    private val iverksattJournalpostId = JournalpostId("j")
    private val iverksattBrevbestillingId = BrevbestillingId("b")
    private val oppgaveId = OppgaveId("2")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saks")
    private val attestant = NavIdentBruker.Attestant("atte")

    private val graphApiResponse = MicrosoftGraphResponse(
        displayName = "Nav Navesen",
        givenName = "",
        mail = "",
        officeLocation = "",
        surname = "",
        userPrincipalName = "",
        id = "",
        jobTitle = "",
    )

    private val utbetalingId = UUID30.randomUUID()
}
