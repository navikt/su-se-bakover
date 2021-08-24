package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory

/**
 * TODO jah: Prøve å finne skillet/abstraksjonen mellom brev og dokument
 *  For domenet er brev mer spesifikt/beskrivende. Samtidig synes jeg det er helt greit å ha en litt bredere abstraksjon i persisteringslaget/klientlaget (Dokument)
 */
internal class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling,
    private val dokumentRepo: DokumentRepo,
    private val sakService: SakService,
    private val personService: PersonService,
) : BrevService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray> {
        return lagPdf(request.brevInnhold)
    }

    override fun journalførBrev(
        request: LagBrevRequest,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeJournalføreBrev, JournalpostId> {
        val brevInnhold = request.brevInnhold
        val brevPdf = lagPdf(brevInnhold).fold(
            { return KunneIkkeJournalføreBrev.KunneIkkeGenereBrev.left() },
            { it },
        )

        return journalfør(
            journalpost = JournalpostFactory.lagJournalpost(
                person = request.person,
                saksnummer = saksnummer,
                brevInnhold = brevInnhold,
                pdf = brevPdf,
            ),
        )
    }

    private fun journalfør(journalpost: Journalpost): Either<KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
            }
    }

    override fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeDistribuereBrev, BrevbestillingId> =
        dokDistFordeling.bestillDistribusjon(journalpostId)
            .mapLeft {
                log.error("Feil ved bestilling av distribusjon for journalpostId:$journalpostId")
                KunneIkkeDistribuereBrev
            }

    override fun lagreDokument(dokument: Dokument.MedMetadata) {
        dokumentRepo.lagre(dokument)
    }

    /**
     * Henter Person fra PersonService med systembruker.
     * Ment brukt fra async-operasjoner som ikke er knyttet til en bruker med token.
     */
    override fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
        val sak = sakService.hentSak(dokumentdistribusjon.dokument.metadata.sakId)
            .getOrHandle { return KunneIkkeJournalføreDokument.KunneIkkeFinneSak.left() }
        val person = personService.hentPersonMedSystembruker(sak.fnr)
            .getOrHandle { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        return dokumentdistribusjon.journalfør {
            journalfør(
                journalpost = JournalpostFactory.lagJournalpost(
                    person = person,
                    saksnummer = sak.saksnummer,
                    dokument = dokumentdistribusjon.dokument,
                ),
            ).mapLeft {
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring
            }
        }.mapLeft {
            when (it) {
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført -> return dokumentdistribusjon.right()
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring -> KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
            }
        }.map {
            dokumentRepo.oppdaterDokumentdistribusjon(it)
            it
        }
    }

    override fun distribuerDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon> {
        return dokumentdistribusjon.distribuerBrev { jounalpostId ->
            distribuerBrev(jounalpostId)
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

    override fun hentDokumenterForDistribusjon(): List<Dokumentdistribusjon> {
        return dokumentRepo.hentDokumenterForDistribusjon()
    }

    override fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument> {
        return when (hentDokumenterForIdType) {
            is HentDokumenterForIdType.Sak -> dokumentRepo.hentForSak(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Søknad -> dokumentRepo.hentForSøknad(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Revurdering -> dokumentRepo.hentForRevurdering(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Vedtak -> dokumentRepo.hentForVedtak(hentDokumenterForIdType.id)
        }
    }

    private fun lagPdf(brevInnhold: BrevInnhold): Either<KunneIkkeLageBrev, ByteArray> {
        return pdfGenerator.genererPdf(brevInnhold)
            .mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePDF }
            .map { it }
    }
}
