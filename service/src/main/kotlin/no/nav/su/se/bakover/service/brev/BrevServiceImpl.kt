package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.Brevdata
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling
) : BrevService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun lagBrevInnhold(request: LagBrevRequest): Brevdata {
        val person = personOppslag.person(request.getFnr()).fold(
            { throw RuntimeException("TODO") },
            { it }
        )
        val personalia = lagPersonalia(person)
        return request.lagBrevdata(personalia)
    }

    private fun lagPersonalia(person: Person) = Brevdata.Personalia(
        dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
        fødselsnummer = person.ident.fnr,
        fornavn = person.navn.fornavn,
        etternavn = person.navn.etternavn,
        adresse = person.adresse?.adressenavn,
        bruksenhet = person.adresse?.bruksenhet,
        husnummer = person.adresse?.husnummer,
        postnummer = person.adresse?.poststed?.postnummer,
        poststed = person.adresse?.poststed?.poststed
    )

    private fun erInnvilget(behandling: Behandling): Boolean {
        val innvilget = listOf(
            Behandling.BehandlingsStatus.SIMULERT,
            Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
            Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
        )

        return innvilget.contains(behandling.status())
    }

    override fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        val loggtema = "Journalføring og sending av vedtaksbrev"

        val person = hentPersonFraFnr(sak.fnr).fold({ return KunneIkkeOppretteJournalpostOgSendeBrev.left() }, { it })

        // TODO temporary redirection - let clients provide the correct request later on
        val innholdeRequest = if (erInnvilget(behandling)) LagBrevRequest.InnvilgetVedtak(behandling)
        else LagBrevRequest.AvslagsVedtak(behandling)

        val brevInnhold = lagBrevInnhold(innholdeRequest)
        val brevPdf = lagPdf(brevInnhold).fold(
            { return KunneIkkeOppretteJournalpostOgSendeBrev.left() },
            { it }
        )

        val journalpostId = dokArkiv.opprettJournalpost(
            Journalpost.Vedtakspost(
                person = person,
                sakId = sak.id.toString(),
                brevdata = brevInnhold,
                pdf = brevPdf
            )
        ).fold(
            {
                log.error("$loggtema: Kunne ikke journalføre i ekstern system (joark/dokarkiv)")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            {
                log.error("$loggtema: Journalført i ekstern system (joark/dokarkiv) OK")
                it
            }
        )

        return sendBrev(journalpostId)
            .mapLeft {
                log.error("$loggtema: Kunne sende brev via ekternt system")
                KunneIkkeOppretteJournalpostOgSendeBrev
            }
            .map {
                log.error("$loggtema: Brev sendt OK via ekstern system")
                it
            }
    }

    override fun lagUtkastTilBrev(
        behandling: Behandling
    ): Either<KunneIkkeLageBrev, ByteArray> {
        return lagBrevPdf(behandling)
    }

    private fun lagPdf(brevdata: Brevdata): Either<KunneIkkeLageBrev, ByteArray> {
        return pdfGenerator.genererPdf(brevdata)
            .mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePDF }
            .map { it }
    }

    private fun lagBrevPdf(
        behandling: Behandling
    ): Either<KunneIkkeLageBrev, ByteArray> {
        // TODO temporary redirection - let clients provide the correct request later on
        val innholdeRequest = if (erInnvilget(behandling)) LagBrevRequest.InnvilgetVedtak(behandling)
        else LagBrevRequest.AvslagsVedtak(behandling)

        val brevInnhold = lagBrevInnhold(innholdeRequest)
        return lagPdf(brevInnhold)
    }

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Fant ikke person i eksternt system basert på sakens fødselsnummer.")
            it
        }.map {
            log.info("Hentet person fra eksternt system OK")
            it
        }

    private fun sendBrev(journalpostId: JournalpostId): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        return dokDistFordeling.bestillDistribusjon(journalpostId).mapLeft { KunneIkkeOppretteJournalpostOgSendeBrev }
    }
}
