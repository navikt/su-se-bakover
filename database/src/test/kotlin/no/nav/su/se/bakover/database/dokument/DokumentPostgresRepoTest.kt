package no.nav.su.se.bakover.database.dokument

import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.oppgaveIdKlage
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.UUID

internal class DokumentPostgresRepoTest {

    @Test
    fun `lagrer og henter dokumenter`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val dokumentRepo = testDataHelper.databaseRepos.dokumentRepo

            val (sak, vedtak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            val revurdering = testDataHelper.persisterRevurderingIverksattInnvilget().second
            val klage = testDataHelper.persisterKlageOversendt(
                vedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second,
                oppgaveId = oppgaveIdKlage,
            )

            // Dette er en snarvei for å teste alle referansene til et dokument og ikke noe som vil oppstå naturlig.
            val original = Dokument.MedMetadata.Vedtak(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                tittel = "tittel",
                generertDokument = "".toByteArray(),
                generertDokumentJson = """{"some": "json"}""",
                metadata = Dokument.Metadata(
                    sakId = sak.id,
                    søknadId = sak.søknader.first().id,
                    vedtakId = vedtak.id,
                    revurderingId = revurdering.id,
                    klageId = klage.id,
                    bestillBrev = false,
                ),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val hentet = dokumentRepo.hentDokument(original.id)!!

            hentet.shouldBeEqualToIgnoringFields(
                original,
                original::generertDokument,
            )

            hentet.generertDokument contentEquals original.generertDokument

            dokumentRepo.hentForSak(sak.id) shouldHaveSize 1
            dokumentRepo.hentForSøknad(sak.søknader.first().id) shouldHaveSize 1
            dokumentRepo.hentForVedtak(vedtak.id) shouldHaveSize 1
            dokumentRepo.hentForRevurdering(revurdering.id) shouldHaveSize 1
            dokumentRepo.hentForKlage(klage.id) shouldHaveSize 1
        }
    }

    @Test
    fun `lagrer bestilling av brev for dokumenter og oppdaterer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val dokumentRepo = testDataHelper.dokumentRepo
            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            val original = Dokument.MedMetadata.Informasjon.Annet(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                tittel = "tittel",
                generertDokument = "".toByteArray(),
                generertDokumentJson = """{"some":"json"}""",
                metadata = Dokument.Metadata(
                    sakId = sak.id,
                    søknadId = sak.søknad.id,
                    bestillBrev = true,
                ),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val dokumentdistribusjon = dokumentRepo.hentDokumenterForDistribusjon().first()

            dokumentdistribusjon.dokument.id shouldBe original.id
            dokumentdistribusjon.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert

            dokumentRepo.oppdaterDokumentdistribusjon(
                dokumentdistribusjon.journalfør { JournalpostId("jp").right() }.getOrHandle {
                    fail { "Skulle fått journalført" }
                },
            )

            val journalført = dokumentRepo.hentDokumenterForDistribusjon().first()

            journalført.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.Journalført(
                JournalpostId("jp"),
            )

            dokumentRepo.oppdaterDokumentdistribusjon(
                journalført.distribuerBrev { BrevbestillingId("brev").right() }.getOrHandle {
                    fail { "Skulle fått bestilt brev" }
                },
            )

            dokumentRepo.hentDokumenterForDistribusjon() shouldBe emptyList()

            val journalførtOgBestiltBrev = dokumentRepo.hentDokumentdistribusjon(dokumentdistribusjon.id)!!

            journalførtOgBestiltBrev.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                JournalpostId("jp"),
                BrevbestillingId("brev"),
            )
        }
    }

    @Test
    fun `henter dokument med status`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val dokumentRepo = testDataHelper.dokumentRepo
            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            val original = Dokument.MedMetadata.Vedtak(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                tittel = "tittel",
                generertDokument = "".toByteArray(),
                generertDokumentJson = """{"some":"json"}""",
                metadata = Dokument.Metadata(
                    sakId = sak.id,
                    søknadId = sak.søknad.id,
                    bestillBrev = true,
                ),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val hentetDokumentUtenStatus = dokumentRepo.hentForSak(sak.id).first()
            hentetDokumentUtenStatus.metadata.journalpostId shouldBe null
            hentetDokumentUtenStatus.metadata.brevbestillingId shouldBe null

            val journalført = dokumentRepo.hentDokumenterForDistribusjon().first()
            dokumentRepo.oppdaterDokumentdistribusjon(
                journalført.journalfør { JournalpostId("jp").right() }.getOrHandle {
                    fail { "Skulle fått journalført" }
                }.distribuerBrev { BrevbestillingId("brev").right() }.getOrHandle {
                    fail { "Skulle fått bestilt brev" }
                },
            )

            val hentetDokumentMedStatus = dokumentRepo.hentForSak(sak.id).first()
            hentetDokumentMedStatus.metadata.journalpostId shouldBe "jp"
            hentetDokumentMedStatus.metadata.brevbestillingId shouldBe "brev"
        }
    }
}
