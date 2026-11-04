package no.nav.su.se.bakover.service.søknad.job

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteJournalpost
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteManglendeSøknadsbehandling
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.slf4j.Logger
import java.util.UUID

internal class FiksSøknaderTest {
    private val logger = mock<Logger>()
    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()

    @Test
    fun `ingen søknader gir vellykket jobb`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = emptyList(),
                oppgaveResultat = emptyList(),
                behandlingResultat = emptyList(),
            ),
        )

        FiksSøknader.run(service, logger) shouldBe JobbResultat.Ok

        verify(service).opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
    }

    @Test
    fun `vellykket reparasjon gir vellykket jobb`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = listOf(mock<Søknad.Journalført.UtenOppgave>().right()),
                oppgaveResultat = listOf(mock<Søknad.Journalført.MedOppgave.IkkeLukket>().right()),
                behandlingResultat = listOf(mock<SimulertSøknadsbehandling>().right()),
            ),
        )

        FiksSøknader.run(service, logger) shouldBe JobbResultat.Ok
    }

    @Test
    fun `journalpostfeil rapporteres selv om andre søknader repareres`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = listOf(
                    KunneIkkeOppretteJournalpost(sakId, søknadId, "Kunne ikke generere PDF").left(),
                    mock<Søknad.Journalført.UtenOppgave>().right(),
                ),
                oppgaveResultat = listOf(mock<Søknad.Journalført.MedOppgave.IkkeLukket>().right()),
                behandlingResultat = emptyList(),
            ),
        )

        FiksSøknader.run(service, logger) shouldBe JobbResultat.DelvisFeilet(
            "Reparasjon av søknader feilet: 1 journalpostfeil, 0 oppgavefeil og 0 behandlingsfeil",
        )
    }

    @Test
    fun `oppgavefeil rapporteres selv om journalføringen lykkes`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = listOf(mock<Søknad.Journalført.UtenOppgave>().right()),
                oppgaveResultat = listOf(
                    KunneIkkeOppretteOppgave(sakId, søknadId, JournalpostId("1"), "Kunne ikke opprette oppgave").left(),
                ),
                behandlingResultat = emptyList(),
            ),
        )

        FiksSøknader.run(service, logger) shouldBe JobbResultat.DelvisFeilet(
            "Reparasjon av søknader feilet: 0 journalpostfeil, 1 oppgavefeil og 0 behandlingsfeil",
        )
    }

    @Test
    fun `behandlingsfeil rapporteres selv om journalpost og oppgave er reparert`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = listOf(mock<Søknad.Journalført.UtenOppgave>().right()),
                oppgaveResultat = listOf(mock<Søknad.Journalført.MedOppgave.IkkeLukket>().right()),
                behandlingResultat = listOf(
                    KunneIkkeOppretteManglendeSøknadsbehandling(sakId, søknadId, "Kunne ikke opprette behandling").left(),
                ),
            ),
        )

        FiksSøknader.run(service, logger) shouldBe JobbResultat.DelvisFeilet(
            "Reparasjon av søknader feilet: 0 journalpostfeil, 0 oppgavefeil og 1 behandlingsfeil",
        )
    }

    @Test
    fun `uventede exceptions slipper fortsatt ut til jobbens feilhåndtering`() {
        val feil = IllegalStateException("Lagring feilet")
        val service = mock<SøknadService> {
            on { opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() } doThrow feil
        }

        shouldThrow<IllegalStateException> {
            FiksSøknader.run(service, logger)
        } shouldBe feil
    }

    private fun serviceMedResultat(resultat: OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat): SøknadService =
        mock {
            on { opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() } doReturn resultat
        }
}
