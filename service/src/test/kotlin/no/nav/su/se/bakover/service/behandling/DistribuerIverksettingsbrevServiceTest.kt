package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import org.junit.jupiter.api.Test

internal class DistribuerIverksettingsbrevServiceTest {

    private val iverksattOppgaveId = OppgaveId("iverksattOppgaveId")
    private val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")
    private val iverksattBrevbestillingId = BrevbestillingId("iverattBrevbestillingId")
    private val innvilgetBehandlingMedJournalpost = BehandlingTestUtils.createOpprettetBehandling().copy(
        status = Behandling.BehandlingsStatus.IVERKSATT_INNVILGET,
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(attestant),
        oppgaveId = iverksattOppgaveId,
        iverksattJournalpostId = iverksattJournalpostId,
        iverksattBrevbestillingId = null,
    )

    @Test
    fun `behandling mangler journalpostId`() {
        val behandling = innvilgetBehandlingMedJournalpost.copy(
            iverksattJournalpostId = null
        )
        val behandlingRepoMock = mock<BehandlingRepo>()
        val brevServiceMock = mock<BrevService>()

        shouldThrowExactly<NullPointerException> {
            DistribuerIverksettingsbrevService(brevServiceMock, behandlingRepoMock).distribuerBrev(
                behandling = behandling,
                incrementMetrics = {}
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock)
    }

    @Test
    fun `kan ikke distribuere brev`() {
        val behandling = innvilgetBehandlingMedJournalpost.copy()
        val behandlingRepoMock = mock<BehandlingRepo> ()
        val brevServiceMock = mock<BrevService> {
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        DistribuerIverksettingsbrevService(brevServiceMock, behandlingRepoMock).distribuerBrev(
            behandling = behandling,
            incrementMetrics = {}
        ) shouldBe DistribuerIverksettingsbrevService.KunneIkkeDistribuereBrev.left()
        verify(brevServiceMock).distribuerBrev(argThat { it shouldBe iverksattJournalpostId })
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock)
    }

    @Test
    fun `distribuerer og persisterer brev`() {
        val behandling = innvilgetBehandlingMedJournalpost.copy()
        val behandlingRepoMock = mock<BehandlingRepo> ()
        val brevServiceMock = mock<BrevService> {
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        DistribuerIverksettingsbrevService(brevServiceMock, behandlingRepoMock).distribuerBrev(
            behandling = behandling,
            incrementMetrics = {}
        ) shouldBe innvilgetBehandlingMedJournalpost.copy(
            iverksattBrevbestillingId = iverksattBrevbestillingId
        ).right()
        verify(brevServiceMock).distribuerBrev(argThat { it shouldBe iverksattJournalpostId })
        verify(behandlingRepoMock).oppdaterIverksattBrevbestillingId(
            behandlingId = argThat { it shouldBe behandling.id },
            bestillingId = argThat { it shouldBe iverksattBrevbestillingId }
        )
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock)
    }
}
