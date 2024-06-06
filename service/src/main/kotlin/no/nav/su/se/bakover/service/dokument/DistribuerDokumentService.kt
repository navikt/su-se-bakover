package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentHendelseSerie
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.KunneIkkeJournalføreOgDistribuereBrev
import dokument.domain.brev.BrevbestillingId
import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import dokument.domain.distribuering.DistribuerDokumentCommand
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.distribuering.KunneIkkeDistribuereJournalførtDokument
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.tilResultat
import no.nav.su.se.bakover.service.journalføring.logResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgangstyring.application.TilgangstyringService
import java.time.Clock

/**
 * Distribuerer 'vanlige' dokumenter (f.eks vedtak). Ment å bli kallt fra en jobb
 * TODO jah: Ideelt sett bør denne ligge i dokument-modulen.
 */
class DistribuerDokumentService(
    private val dokDistFordeling: DokDistFordeling,
    private val dokumentRepo: DokumentRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val distribuerDokumentHendelserKonsument: DistribuerDokumentHendelserKonsument,
    private val tilgangstyringService: TilgangstyringService,
    private val clock: Clock,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun distribuer(): List<JournalføringOgDistribueringsResultat> = dokumentRepo.hentDokumenterForDistribusjon()
        .map { distribusjon ->
            distribuerDokument(
                distribusjon,
                distribusjon.dokument.distribueringsadresse,
            ).tilResultat(distribusjon, log)
        }
        .also { it.logResultat("Dokumentdistribusjon", log) }

    private fun distribuerDokument(
        dokumentdistribusjon: Dokumentdistribusjon,
        distribueringsadresse: Distribueringsadresse? = null,
    ): Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon> {
        return dokumentdistribusjon.distribuerBrev(clock) { jounalpostId ->
            dokDistFordeling.bestillDistribusjon(
                journalpostId = jounalpostId,
                distribusjonstype = dokumentdistribusjon.dokument.distribusjonstype,
                distribusjonstidspunkt = dokumentdistribusjon.dokument.distribusjonstidspunkt,
                distribueringsadresse = distribueringsadresse,
            )
        }.mapLeft {
            when (it) {
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> return dokumentdistribusjon.right()
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.OppdatertFailures -> {
                    // Vi må lagre distribusjonFailures for at backoff-algoritmen skal fungere.
                    dokumentRepo.oppdaterDokumentdistribusjon(it.dokumentdistribusjon)
                    KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev
                }

                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.MåJournalføresFørst -> KunneIkkeBestilleBrevForDokument.MåJournalføresFørst
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.ForTidligÅPrøvePåNytt -> KunneIkkeBestilleBrevForDokument.ForTidligÅPrøvePåNytt
            }
        }.map {
            dokumentRepo.oppdaterDokumentdistribusjon(it)
            it
        }
    }

    fun distribuerDokument(command: DistribuerDokumentCommand): Either<KunneIkkeDistribuereJournalførtDokument, Dokument.MedMetadata> {
        tilgangstyringService.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeDistribuereJournalførtDokument.IkkeTilgang(command.dokumentId).left()
        }
        // TODO jah: Merk at i første omgang henter vi kun dokumenter fra den dokument_distribusjons-tabellen. Vi må også hente dokumenter fra hendelser-tabellen.
        val dokumentId = command.dokumentId

        dokumentRepo.hentDokumentdistribusjonForDokumentId(dokumentId)?.let {
            return distribuerDokumentForDokumentRepo(it, command.distribueringsadresse)
        }
        dokumentHendelseRepo.hentDokumentHendelserForSakId(command.sakId).hentSerieForDokumentId(dokumentId)?.let {
            return distribuerDokumentForHendelse(it, command.distribueringsadresse)
        }

        return KunneIkkeDistribuereJournalførtDokument.FantIkkeDokument(dokumentId).left()
    }

    private fun distribuerDokumentForDokumentRepo(
        dokumentdistribusjon: Dokumentdistribusjon,
        distribueringsadresse: Distribueringsadresse,
    ): Either<KunneIkkeDistribuereJournalførtDokument, Dokument.MedMetadata> {
        val dokument = dokumentdistribusjon.dokument
        val dokumentId = dokument.id
        if (dokument.erBrevBestilt()) {
            return KunneIkkeDistribuereJournalførtDokument.AlleredeDistribuert(
                dokumentId = dokumentId,
                journalpostId = JournalpostId(dokument.journalpostId!!),
                brevbestillingId = BrevbestillingId(dokument.brevbestillingId!!),
            ).left()
        }
        if (!dokument.erJournalført()) {
            return KunneIkkeDistribuereJournalførtDokument.IkkeJournalført(dokumentId).left()
        }
        val journalpostId = JournalpostId(dokument.journalpostId!!)
        // Siden en saksbehandler har trigget denne kommandoen, så kan vi ignorere backoff-strategien.
        return dokumentdistribusjon.distribuerBrev(clock, ignoreBackoff = true) {
            dokDistFordeling.bestillDistribusjon(
                journalpostId = it,
                distribusjonstype = dokument.distribusjonstype,
                distribusjonstidspunkt = dokument.distribusjonstidspunkt,
                distribueringsadresse = distribueringsadresse,
            )
        }.mapLeft {
            KunneIkkeDistribuereJournalførtDokument.FeilVedDistribusjon(
                dokumentId = dokumentId,
                journalpostId = journalpostId,
            )
        }.map {
            dokumentRepo.oppdaterDokumentdistribusjon(it)
            it.dokument
        }
    }

    private fun distribuerDokumentForHendelse(
        dokumentHendelseSerie: DokumentHendelseSerie,
        distribueringsadresse: Distribueringsadresse,
    ): Either<KunneIkkeDistribuereJournalførtDokument, Dokument.MedMetadata> {
        val generertDokumentHendelse = dokumentHendelseSerie.generertDokument()

        val dokumentId = dokumentHendelseSerie.dokumentId
        if (!dokumentHendelseSerie.harJournalført()) {
            return KunneIkkeDistribuereJournalførtDokument.IkkeJournalført(dokumentId).left()
        }
        if (dokumentHendelseSerie.harBestiltBrev()) {
            return KunneIkkeDistribuereJournalførtDokument.AlleredeDistribuert(
                dokumentId = dokumentId,
                journalpostId = dokumentHendelseSerie.journalpostIdOrNull()!!,
                brevbestillingId = dokumentHendelseSerie.brevbestillingIdOrNull()!!,
            ).left()
        }
        return distribuerDokumentHendelserKonsument.ditribuerForSakId(
            sakId = dokumentHendelseSerie.sakId,
            hendelseId = generertDokumentHendelse.hendelseId,
            correlationId = getOrCreateCorrelationIdFromThreadLocal(),
            distribueringsadresse,
        ).map {
            dokumentHendelseRepo.hentDokumentMedMetadataForSakIdOgDokumentId(
                sakId = dokumentHendelseSerie.sakId,
                dokumentId = dokumentId,
            )!!
        }
    }
}
