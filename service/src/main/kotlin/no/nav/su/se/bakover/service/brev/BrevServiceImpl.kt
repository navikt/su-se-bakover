package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling
) : BrevService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray> {
        return lagPdf(lagBrevInnhold(request))
    }

    override fun journalførBrev(request: LagBrevRequest, sakId: UUID): Either<KunneIkkeJournalføreBrev, JournalpostId> {

        val brevInnhold = lagBrevInnhold(request)
        val brevPdf = lagPdf(brevInnhold).fold(
            { return KunneIkkeJournalføreBrev.KunneIkkeGenereBrev.left() },
            { it }
        )

        return dokArkiv.opprettJournalpost(
            JournalpostFactory.lagJournalpost(
                person = request.getPerson(),
                sakId = sakId,
                brevInnhold = brevInnhold,
                pdf = brevPdf
            )
        ).mapLeft {
            log.error("Journalføring: Kunne ikke journalføre i ekstern system (joark/dokarkiv)")
            KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
        }.map { it }
    }

    override fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeDistribuereBrev, BrevbestillingId> =
        dokDistFordeling.bestillDistribusjon(journalpostId)
            .mapLeft {
                log.error("Feil ved bestilling av distribusjon for journalpostId:$journalpostId")
                KunneIkkeDistribuereBrev
            }

    private fun lagBrevInnhold(request: LagBrevRequest): BrevInnhold {
        val personalia = lagPersonalia(request.getPerson())
        return request.lagBrevInnhold(personalia)
    }

    private fun lagPersonalia(person: Person) = BrevInnhold.Personalia(
        dato = LocalDate.now().ddMMyyyy(),
        fødselsnummer = person.ident.fnr,
        fornavn = person.navn.fornavn,
        etternavn = person.navn.etternavn,
    )

    private fun lagPdf(brevInnhold: BrevInnhold): Either<KunneIkkeLageBrev, ByteArray> {
        return pdfGenerator.genererPdf(brevInnhold)
            .mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePDF }
            .map { it }
    }
}
