package no.nav.su.se.bakover.service.søknad.job

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteJournalpost
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.slf4j.Logger
import java.util.UUID

internal class FiksSøknaderUtenOppgaveTest {
    private val logger = mock<Logger>()
    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()

    @Test
    fun `ingen søknader gir vellykket jobb`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = emptyList(),
                oppgaveResultat = emptyList(),
            ),
        )

        FiksSøknaderUtenOppgave.run(service, logger) shouldBe JobbResultat.Ok

        verify(service).opprettManglendeJournalpostOgOppgave()
    }

    @Test
    fun `vellykket reparasjon gir vellykket jobb`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(mock<Søknad.Journalført.UtenOppgave>().right()),
                oppgaveResultat = listOf(mock<Søknad.Journalført.MedOppgave.IkkeLukket>().right()),
            ),
        )

        FiksSøknaderUtenOppgave.run(service, logger) shouldBe JobbResultat.Ok
    }

    @Test
    fun `journalpostfeil rapporteres selv om andre søknader repareres`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(
                    KunneIkkeOppretteJournalpost(sakId, søknadId, "Kunne ikke generere PDF").left(),
                    mock<Søknad.Journalført.UtenOppgave>().right(),
                ),
                oppgaveResultat = listOf(mock<Søknad.Journalført.MedOppgave.IkkeLukket>().right()),
            ),
        )

        FiksSøknaderUtenOppgave.run(service, logger) shouldBe JobbResultat.DelvisFeilet(
            "Reparasjon av søknader feilet: 1 journalpostfeil og 0 oppgavefeil",
        )
    }

    @Test
    fun `oppgavefeil rapporteres selv om journalføringen lykkes`() {
        val service = serviceMedResultat(
            OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(mock<Søknad.Journalført.UtenOppgave>().right()),
                oppgaveResultat = listOf(
                    KunneIkkeOppretteOppgave(sakId, søknadId, JournalpostId("1"), "Kunne ikke opprette oppgave").left(),
                ),
            ),
        )

        FiksSøknaderUtenOppgave.run(service, logger) shouldBe JobbResultat.DelvisFeilet(
            "Reparasjon av søknader feilet: 0 journalpostfeil og 1 oppgavefeil",
        )
    }

    @Test
    fun `uventede exceptions slipper fortsatt ut til jobbens feilhåndtering`() {
        val feil = IllegalStateException("Lagring feilet")
        val service = mock<SøknadService> {
            on { opprettManglendeJournalpostOgOppgave() } doThrow feil
        }

        shouldThrow<IllegalStateException> {
            FiksSøknaderUtenOppgave.run(service, logger)
        } shouldBe feil
    }

    private fun serviceMedResultat(resultat: OpprettManglendeJournalpostOgOppgaveResultat): SøknadService =
        mock {
            on { opprettManglendeJournalpostOgOppgave() } doReturn resultat
        }
}
