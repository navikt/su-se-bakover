package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling
) : BrevService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray> {
        val person = hentPersonFraFnr(request.getFnr()).fold(
            { return KunneIkkeLageBrev.FantIkkePerson.left() },
            { it }
        )
        return lagPdf(lagBrevInnhold(request, person))
    }

    override fun journalførBrev(request: LagBrevRequest, sakId: UUID): Either<KunneIkkeJournalføreBrev, JournalpostId> {
        val person = hentPersonFraFnr(request.getFnr()).fold(
            { return KunneIkkeJournalføreBrev.FantIkkePerson.left() },
            { it }
        )
        val brevInnhold = lagBrevInnhold(request, person)
        val brevPdf = lagPdf(brevInnhold).fold(
            { return KunneIkkeJournalføreBrev.KunneIkkeGenereBrev.left() },
            { it }
        )

        return dokArkiv.opprettJournalpost(
            JournalpostFactory.lagJournalpost(
                person = person,
                sakId = sakId,
                brevInnhold = brevInnhold,
                pdf = brevPdf
            )
        ).mapLeft {
            log.error("Journalføring: Kunne ikke journalføre i ekstern system (joark/dokarkiv)")
            KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
        }.map { it }
    }

    override fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeDistribuereBrev, String> =
        dokDistFordeling.bestillDistribusjon(journalpostId)
            .mapLeft {
                log.error("Feil ved bestilling av distribusjon for journalpostId:$journalpostId")
                KunneIkkeDistribuereBrev
            }

    private fun lagBrevInnhold(request: LagBrevRequest, person: Person): BrevInnhold {
        val personalia = lagPersonalia(person)
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

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Fant ikke person i eksternt system basert på sakens fødselsnummer.")
            it
        }.map {
            log.info("Hentet person fra eksternt system OK")
            it
        }
}
