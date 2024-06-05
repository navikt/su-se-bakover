package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.KunneIkkeJournalføreOgDistribuereBrev
import dokument.domain.brev.BrevbestillingId
import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import dokument.domain.brev.KunneIkkeDistribuereBrev
import dokument.domain.distribuering.DistribuerDokumentCommand
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.distribuering.KunneIkkeDistribuereJournalførtDokument
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.tilResultat
import no.nav.su.se.bakover.service.journalføring.logResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgangstyring.application.TilgangstyringService

/**
 * Distribuerer 'vanlige' dokumenter (f.eks vedtak). Ment å bli kallt fra en jobb
 * TODO jah: Ideelt sett bør denne ligge i dokument-modulen.
 */
class DistribuerDokumentService(
    private val dokDistFordeling: DokDistFordeling,
    private val dokumentRepo: DokumentRepo,
    private val tilgangstyringService: TilgangstyringService,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun distribuer(): List<JournalføringOgDistribueringsResultat> = dokumentRepo.hentDokumenterForDistribusjon()
        .map { dokument -> distribuerDokument(dokument).tilResultat(dokument, log) }
        .also { it.logResultat("Distribuer dokument", log) }

    private fun distribuerDokument(
        dokumentdistribusjon: Dokumentdistribusjon,
        distribueringsadresse: Distribueringsadresse? = null,
    ): Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon> {
        return dokumentdistribusjon.distribuerBrev { jounalpostId ->
            distribuerBrev(
                journalpostId = jounalpostId,
                distribusjonstype = dokumentdistribusjon.dokument.distribusjonstype,
                distribusjonstidspunkt = dokumentdistribusjon.dokument.distribusjonstidspunkt,
                distribueringsadresse = distribueringsadresse,
            )
                .mapLeft {
                    KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                        journalpostId = jounalpostId,
                    )
                }
        }.mapLeft {
            when (it) {
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> return dokumentdistribusjon.right()
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.MåJournalføresFørst -> KunneIkkeBestilleBrevForDokument.MåJournalføresFørst
            }
        }.map {
            dokumentRepo.oppdaterDokumentdistribusjon(it)
            it
        }
    }

    private fun distribuerBrev(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
        distribueringsadresse: Distribueringsadresse? = null,
    ): Either<KunneIkkeDistribuereBrev, BrevbestillingId> {
        return dokDistFordeling.bestillDistribusjon(
            journalpostId,
            distribusjonstype,
            distribusjonstidspunkt,
            distribueringsadresse,
        )
            .mapLeft {
                log.error("Feil ved bestilling av distribusjon for journalpostId:$journalpostId")
                KunneIkkeDistribuereBrev
            }
    }

    fun distribuerDokument(command: DistribuerDokumentCommand): Either<KunneIkkeDistribuereJournalførtDokument, Dokument.MedMetadata> {
        tilgangstyringService.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeDistribuereJournalførtDokument.IkkeTilgang(command.dokumentId).left()
        }
        // TODO jah: Merk at i første omgang henter vi kun dokumenter fra den dokument_distribusjons-tabellen. Vi må også hente dokumenter fra hendelser-tabellen.
        val dokumentId = command.dokumentId

        val dokumentdistribusjon = dokumentRepo.hentDokumentdistribusjonForDokumentId(dokumentId)
            ?: return KunneIkkeDistribuereJournalførtDokument.FantIkkeDokument(dokumentId).left()

        val dokument = dokumentdistribusjon.dokument
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
        return distribuerDokument(dokumentdistribusjon, command.distribueringsadresse).mapLeft { feil ->
            KunneIkkeDistribuereJournalførtDokument.FeilVedDistribusjon(
                dokumentId = dokumentId,
                journalpostId = JournalpostId(dokument.journalpostId!!),
                brevbestillingId = BrevbestillingId(dokument.brevbestillingId!!),
                underliggendeFeil = feil,
            )
        }.map {
            it.dokument
        }
    }
}
