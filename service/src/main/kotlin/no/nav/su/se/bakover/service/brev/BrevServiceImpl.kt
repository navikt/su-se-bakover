package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.KunneIkkeGenererePdf
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold.Companion.lagLukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.VedtakInnhold
import no.nav.su.se.bakover.domain.VedtakInnhold.Companion.lagVedtaksinnhold
import no.nav.su.se.bakover.domain.brev.Brevinnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplate
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

    override fun lagUtkastTilBrev(
        behandling: Behandling
    ): Either<KunneIkkeLageBrev, ByteArray> {
        return sakService.hentSak(behandling.sakId)
            .mapLeft { KunneIkkeLageBrev.FantIkkeSak }
            .flatMap { sak ->
                hentPersonFraFnr(sak.fnr)
                    .mapLeft { KunneIkkeLageBrev.FantIkkePerson }
                    .flatMap { person ->
                        lagBrevPdf(behandling, person)
                            .mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePdf }
                            .map { it }
                    }
            }
    }

    private fun lagBrevPdf(
        behandling: Behandling,
        person: Person
    ): Either<KunneIkkeGenererePdf, ByteArray> {
        val vedtakinnhold = lagVedtaksinnhold(person, behandling)

        val pdfTemplate = when (vedtakinnhold) {
            is VedtakInnhold.Innvilgelsesvedtak -> PdfTemplate.VedtakInnvilget
            is VedtakInnhold.Avslagsvedtak -> PdfTemplate.VedtakAvslag
        }

        return pdfGenerator.genererPdf(objectMapper.writeValueAsString(vedtakinnhold), pdfTemplate)
            .mapLeft {
                log.error("Kunne ikke generere brevinnhold")
                it
            }
            .map {
                log.info("Generert brevinnhold OK")
                it
            }
    }

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Fant ikke person i eksternt system basert på sakens fødselsnummer.")
            it
        }.map {
            log.info("Hentet person fra eksternt system OK")
            it
        }

    private fun sendBrev(journalPostId: String): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        return dokDistFordeling.bestillDistribusjon(journalPostId).mapLeft { KunneIkkeOppretteJournalpostOgSendeBrev }
    }

    override fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        val loggtema = "Journalføring og sending av vedtaksbrev"

        val person = hentPersonFraFnr(sak.fnr).fold({ return KunneIkkeOppretteJournalpostOgSendeBrev.left() }, { it })
        val brevInnhold = lagBrevPdf(behandling, person).fold(
            { return KunneIkkeOppretteJournalpostOgSendeBrev.left() },
            { it }
        )

        val journalPostId = dokArkiv.opprettJournalpost(
            Journalpost.Vedtakspost(
                person = person,
                sakId = sak.id.toString(),
                vedtakInnhold = lagVedtaksinnhold(person, behandling),
                pdf = brevInnhold
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

        return sendBrev(journalPostId)
            .mapLeft {
                log.error("$loggtema: Kunne sende brev via ekternt system")
                KunneIkkeOppretteJournalpostOgSendeBrev
            }
            .map {
                log.error("$loggtema: Brev sendt OK via ekstern system")
                it
            }
    }

    override fun journalførLukketSøknadOgSendBrev(
        sakId: UUID,
        søknad: Søknad,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        val loggtema = "Journalføring og lukking av søknad"
        val person = sakService.hentSak(sakId).fold(
            ifLeft = {
                log.error("$loggtema: fant ikke sak for sakId: $sakId")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            ifRight = { sak ->
                hentPersonFraFnr(sak.fnr).fold(
                    ifLeft = {
                        log.error("$loggtema: kunne ikke hente person for sakId: $sakId")
                        return KunneIkkeOppretteJournalpostOgSendeBrev.left()
                    },
                    ifRight = { person ->
                        log.info("Hentet Person for lukking av søknad OK")
                        person
                    }
                )
            }
        )

        val lukketSøknadBrevPdf = genererLukketSøknadBrevPdf(
            person = person,
            søknad = søknad,
            lukketSøknad = lukketSøknad
        ).fold(
            ifLeft = {
                log.error("$loggtema: kunne ikke generere pdf for å lukke søknad")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            ifRight = {
                log.info("Generert brev for lukke av søknad OK")
                it
            }
        )

        val journalPostId = dokArkiv.opprettJournalpost(
            Journalpost.LukketSøknadJournalpostRequest(
                person = person,
                sakId = sakId,
                lukketSøknadBrevinnhold = lagLukketSøknadBrevinnhold(
                    person = person,
                    søknad = søknad,
                    lukketSøknad = lukketSøknad
                ),
                pdf = lukketSøknadBrevPdf
            )
        ).fold(
            ifLeft = {
                log.error("$loggtema: kunne ikke få journalpost id")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            ifRight = {
                log.info("Journalpost id for lukking av søknad OK. Journalpost-id: $it")
                it
            }
        )

        return sendBrev(journalPostId)
    }

    private fun genererLukketSøknadBrevPdf(
        person: Person,
        søknad: Søknad,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeGenererePdf, ByteArray> {
        val lukketSøknadBrevinnhold =
            lagLukketSøknadBrevinnhold(
                person = person,
                søknad = søknad,
                lukketSøknad = lukketSøknad
            )
        val pdfTemplate = when (lukketSøknadBrevinnhold) {
            is LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold -> PdfTemplate.TrukketSøknad
            else -> throw java.lang.RuntimeException("template kan bare være trukket")
        }

        return pdfGenerator.genererPdf(
            innholdJson = objectMapper.writeValueAsString(lukketSøknadBrevinnhold),
            pdfTemplate = pdfTemplate
        )
            .mapLeft {
                log.error("Kunne ikke generere brevinnhold")
                it
            }
            .map {
                log.info("Generert brevinnhold OK")
                it
            }
    }
}
