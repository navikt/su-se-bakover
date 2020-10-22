package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.brev.Brevinnhold
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling,
    private val sakService: SakService
) : BrevService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagBrev(brevinnhold: Brevinnhold): Either<KunneIkkeLageBrev, ByteArray> =
        pdfGenerator.genererPdf(brevinnhold.toJson(), brevinnhold.pdfTemplate())
            .mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePdf }

    // TODO could probably refactor away from having to retrieve sak/fnr and stuff.
    override fun journalførBrev(brevinnhold: Brevinnhold, sakId: UUID): Either<KunneIkkeJournalføreBrev, String> {
        val sak = sakService.hentSak(sakId).fold(
            { return KunneIkkeJournalføreBrev.FantIkkeSak.left() },
            { it }
        )
        val person = hentPersonFraFnr(sak.fnr).fold(
            { return KunneIkkeJournalføreBrev.FantIkkePerson.left() },
            { it }
        )

        val pdf = lagBrev(brevinnhold).fold(
            { return KunneIkkeJournalføreBrev.KunneIkkeGenererePdf.left() },
            { it }
        )
        // TODO fix for all kinds of journalpost, or pass actual as input.
        return dokArkiv.opprettJournalpost(
            JournalpostFactory.lagJournalpost(
                person = person,
                sakId = sak.id.toString(),
                brevinnhold = brevinnhold,
                pdf = pdf
            )
        ).mapLeft {
            KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
        }.map {
            it
        }
    }

    override fun distribuerBrev(journalPostId: String): Either<KunneIkkeDistribuereBrev, String> {
        return dokDistFordeling.bestillDistribusjon(journalPostId)
            .mapLeft { KunneIkkeDistribuereBrev }
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
