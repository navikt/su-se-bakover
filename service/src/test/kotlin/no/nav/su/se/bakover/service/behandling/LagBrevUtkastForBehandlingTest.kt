package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.BEREGNET_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.createService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LagBrevUtkastForBehandlingTest {
    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        telefonnummer = null,
        adresse = null,
        statsborgerskap = null,
        kjønn = null,
        adressebeskyttelse = null,
        skjermet = null,
        kontaktinfo = null,
        vergemål = null,
        fullmakt = null,
    )

    @Test
    fun `lager brevutkast for avslag`() {
        val pdf = "pdf-doc".toByteArray()
        val behandling = beregnetBehandling().copy(status = BEREGNET_AVSLAG)
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).lagBrevutkast(behandlingId)

        response shouldBe pdf.right()
        verify(brevServiceMock).lagBrev(argThat { it.shouldBeTypeOf<AvslagBrevRequest>() })
    }

    @Test
    fun `lager brevutkast for innvilgelse`() {
        val pdf = "pdf-doc".toByteArray()
        val behandling = behandlingTilAttestering()
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).lagBrevutkast(behandlingId)

        response shouldBe pdf.right()
        verify(personOppslagMock).person(argThat { it shouldBe fnr })
        verify(brevServiceMock).lagBrev(argThat { it.shouldBeTypeOf<LagBrevRequest.InnvilgetVedtak>() })
    }

    @Test
    fun `svarer med feil dersom behandling ikke finnes`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn null
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).lagBrevutkast(behandlingId)

        response shouldBe KunneIkkeLageBrevutkast.FantIkkeBehandling.left()
    }

    @Test
    fun `svarer med feil dersom laging av brev feiler`() {
        val behandling = behandlingTilAttestering()
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).lagBrevutkast(behandlingId)

        response shouldBe KunneIkkeLageBrevutkast.KunneIkkeLageBrev.left()
    }

    private fun beregnetBehandling() = BehandlingFactory(mock()).createBehandling(
        sakId = sakId,
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId,
        ),
        status = Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
        beregning = beregning,
        fnr = fnr,
        oppgaveId = oppgaveId,
        saksbehandler = Saksbehandler("ZZ992299")
    )

    private fun behandlingTilAttestering() = beregnetBehandling().copy(
        simulering = simulering,
        status = IVERKSATT_INNVILGET,
        saksbehandler = saksbehandler
    )

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )
}
