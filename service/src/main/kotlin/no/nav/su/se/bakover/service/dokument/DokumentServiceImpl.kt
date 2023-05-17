package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokarkiv.lagJournalpost
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.service.dokument.JournalføringsResultat.Companion.feil
import no.nav.su.se.bakover.service.dokument.JournalføringsResultat.Companion.ok
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DokumentServiceImpl(
    private val dokArkiv: DokArkiv,
    private val sakService: SakService,
    private val personService: PersonService,
    private val dokumentRepo: DokumentRepo,
    private val dokumentSkattRepo: DokumentSkattRepo,
) : DokumentService {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun journalførDokumenter() {
        val dokumenterSomMåJournalføres = dokumentRepo.hentDokumenterForJournalføring()
        val skatteDokumenterSomMåJournalføres = dokumentSkattRepo.hentDokumenterForJournalføring()

        val dokumenterResultat = dokumenterSomMåJournalføres.map { dokumentdistribusjon ->
            journalførDokument(dokumentdistribusjon)
                .map { JournalføringsResultat.Ok(dokumentdistribusjon.id) }
                .mapLeft { JournalføringsResultat.Feil(dokumentdistribusjon.id) }
        }

        val skatteDokumenterResultat = skatteDokumenterSomMåJournalføres.map { skattedokument ->
            journalførSkattedokument(skattedokument)
                .map { JournalføringsResultat.Ok(it.id) }
                .mapLeft { JournalføringsResultat.Feil(skattedokument.id) }
        }

        val resultat = dokumenterResultat + skatteDokumenterResultat
        resultat.ifNotEmpty {
            val ok = this.ok()
            val feil = this.feil()
            if (feil.isEmpty()) {
                log.info("Journalførte/distribuerte distribusjonsIDene: $ok")
            } else {
                log.error("Kunne ikke journalføre/distribuere distribusjonsIDene: $feil. Disse gikk ok: $ok")
            }
        }
    }


    private fun journalførSkattedokument(skattedokument: Skattedokument.Generert): Either<KunneIkkeJournalføreDokument, Skattedokument.Journalført> {
        val sakInfo = sakService.hentSakInfo(skattedokument.sakid).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${skattedokument.sakid}")
        }
        val person = personService.hentPersonMedSystembruker(sakInfo.fnr)
            .getOrElse { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        return journalfør(skattedokument.lagJournalpost(sakInfo, person))
            .mapLeft { KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost }
            .map {
                val tilJournalført = skattedokument.tilJournalført(it)
                dokumentSkattRepo.lagre(tilJournalført)
                tilJournalført
            }
    }

    private fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
        val sakInfo = sakService.hentSakInfo(dokumentdistribusjon.dokument.metadata.sakId).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${dokumentdistribusjon.dokument.metadata.sakId}")
        }
        val person = personService.hentPersonMedSystembruker(sakInfo.fnr)
            .getOrElse { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        val journalførtDokument = dokumentdistribusjon.journalfør {
            journalfør(
                journalpost = JournalpostFactory.lagJournalpost(
                    person = person,
                    saksnummer = sakInfo.saksnummer,
                    dokument = dokumentdistribusjon.dokument,
                    sakstype = sakInfo.type,
                ),
            ).mapLeft { KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring }
        }

        return journalførtDokument
            .mapLeft { KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost }
            .onRight {
                dokumentRepo.oppdaterDokumentdistribusjon(it)
            }
    }


    private fun journalfør(journalpost: Journalpost): Either<KunneIkkeJournalføreBrev, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
            }
    }


}
