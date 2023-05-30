package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.domain.brev.KunneIkkeBestilleBrevForDokument
import no.nav.su.se.bakover.domain.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.logResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Distribuerer 'vanlige' dokumenter (f.eks vedtak). Ment å bli kallt fra en jobb
 */
class DistribuerDokumentService(
    private val dokDistFordeling: DokDistFordeling,
    private val dokumentRepo: DokumentRepo,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun distribuer() {
        dokumentRepo.hentDokumenterForDistribusjon().map { dokument ->
            distribuerDokument(dokument)
                .map { JournalføringOgDistribueringsResultat.Ok(it.id) }
                .mapLeft { JournalføringOgDistribueringsResultat.Feil(dokument.id) }
        }.logResultat(log)
    }

    /**
     * Internal for testing.
     * sikkert fordi man ikke vil skrive så mye :shrug: kan bli gjort private hvis man tester mulige feil-caser
     */
    internal fun distribuerDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon> {
        return dokumentdistribusjon.distribuerBrev { jounalpostId ->
            distribuerBrev(
                jounalpostId,
                dokumentdistribusjon.dokument.distribusjonstype,
                dokumentdistribusjon.dokument.distribusjonstidspunkt,
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

    /**
     * Internal for testing.
     * sikkert fordi man ikke vil skrive så mye :shrug: kan bli gjort private hvis man tester mulige feil-caser
     */
    internal fun distribuerBrev(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
    ): Either<KunneIkkeDistribuereBrev, BrevbestillingId> =
        dokDistFordeling.bestillDistribusjon(journalpostId, distribusjonstype, distribusjonstidspunkt)
            .mapLeft {
                log.error("Feil ved bestilling av distribusjon for journalpostId:$journalpostId")
                KunneIkkeDistribuereBrev
            }
}
