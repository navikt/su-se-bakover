package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokarkiv.JournalpostSkatt.Companion.lagJournalpost
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.logResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * journalfører dokumenter tilhørende skatt. Ment å bli kallt fra en jobb
 */
class JournalførSkattDokumentService(
    private val dokArkiv: DokArkiv,
    private val sakService: SakService,
    private val personService: PersonService,
    private val dokumentSkattRepo: DokumentSkattRepo,
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun journalfør() {
        val skatteDokumenterSomMåJournalføres = dokumentSkattRepo.hentDokumenterForJournalføring()

        skatteDokumenterSomMåJournalføres.map { skattedokument ->
            journalførSkattedokument(skattedokument)
                .map { JournalføringOgDistribueringsResultat.Ok(it.id) }
                .mapLeft {
                    log.error(
                        "Kunne ikke journalføre skattedokument ${skattedokument.id}: $it",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    JournalføringOgDistribueringsResultat.Feil(skattedokument.id)
                }
        }.logResultat("Journalføring skatt", log)
    }

    private fun journalførSkattedokument(skattedokument: Skattedokument.Generert): Either<KunneIkkeJournalføreDokument, Skattedokument.Journalført> {
        val sakInfo = sakService.hentSakInfo(skattedokument.sakid).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${skattedokument.sakid}")
        }
        val person = personService.hentPersonMedSystembruker(sakInfo.fnr)
            .getOrElse { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        return journalfør(skattedokument.lagJournalpost(person, sakInfo)).map {
            val tilJournalført = skattedokument.tilJournalført(it)
            dokumentSkattRepo.lagre(tilJournalført)
            tilJournalført
        }
    }

    private fun journalfør(journalpost: Journalpost): Either<KunneIkkeJournalføreDokument, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
            }
    }
}
