package no.nav.su.se.bakover.database.dokument

import arrow.core.getOrElse
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.Dokumentdistribusjon
import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.brev.BrevbestillingId
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.pdfATom
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
            )

            // Dette er en snarvei for å teste alle referansene til et dokument og ikke noe som vil oppstå naturlig.
            val original = Dokument.MedMetadata.Vedtak(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                tittel = "tittel",
                generertDokument = pdfATom(),
                generertDokumentJson = """{"some": "json"}""",
                metadata = Dokument.Metadata(
                    sakId = sak.id,
                    søknadId = sak.søknader.first().id,
                    vedtakId = vedtak.id,
                    revurderingId = revurdering.id.value,
                    klageId = klage.id.value,
                ),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val hentet = dokumentRepo.hentDokument(original.id)!!

            hentet.shouldBeEqualToIgnoringFields(
                original,
                original::generertDokument,
            )

            hentet.generertDokument shouldBe original.generertDokument

            dokumentRepo.hentForSak(sak.id) shouldHaveSize 1
            dokumentRepo.hentForSøknad(sak.søknader.first().id) shouldHaveSize 1
            dokumentRepo.hentForVedtak(vedtak.id) shouldHaveSize 1
            dokumentRepo.hentForRevurdering(revurdering.id.value) shouldHaveSize 1
            dokumentRepo.hentForKlage(klage.id.value) shouldHaveSize 1
            dokumentRepo.hentDokumentdistribusjonForDokumentId(original.id)!!.also {
                it shouldBe Dokumentdistribusjon(
                    // Denne genereres i repoets lagre funksjon
                    id = it.id,
                    opprettet = original.opprettet,
                    endret = original.opprettet,
                    dokument = original,
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                )
            }
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
                generertDokument = pdfATom(),
                generertDokumentJson = """{"some":"json"}""",
                metadata = Dokument.Metadata(sakId = sak.id, søknadId = sak.søknad.id),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val dokumentdistribusjon = dokumentRepo.hentDokumenterForJournalføring().first()

            dokumentdistribusjon.dokument.id shouldBe original.id
            dokumentdistribusjon.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert

            dokumentRepo.oppdaterDokumentdistribusjon(
                dokumentdistribusjon.journalfør { JournalpostId("jp").right() }.getOrElse {
                    fail { "Skulle fått journalført" }
                },
            )

            val journalført = dokumentRepo.hentDokumenterForDistribusjon().first()

            journalført.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.Journalført(
                JournalpostId("jp"),
            )

            dokumentRepo.oppdaterDokumentdistribusjon(
                journalført.distribuerBrev { BrevbestillingId("brev").right() }.getOrElse {
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
                generertDokument = pdfATom(),
                generertDokumentJson = """{"some":"json"}""",
                metadata = Dokument.Metadata(sakId = sak.id, søknadId = sak.søknad.id),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val hentetDokumentUtenStatus = dokumentRepo.hentForSak(sak.id).first()
            hentetDokumentUtenStatus.metadata.journalpostId shouldBe null
            hentetDokumentUtenStatus.metadata.brevbestillingId shouldBe null

            val journalført = dokumentRepo.hentDokumenterForJournalføring().first()
            dokumentRepo.oppdaterDokumentdistribusjon(
                journalført.journalfør { JournalpostId("jp").right() }.getOrElse {
                    fail { "Skulle fått journalført" }
                }.distribuerBrev { BrevbestillingId("brev").right() }.getOrElse {
                    fail { "Skulle fått bestilt brev" }
                },
            )

            val hentetDokumentMedStatus = dokumentRepo.hentForSak(sak.id).first()
            hentetDokumentMedStatus.metadata.journalpostId shouldBe "jp"
            hentetDokumentMedStatus.metadata.brevbestillingId shouldBe "brev"
        }
    }
}
