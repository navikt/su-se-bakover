package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.dokument.DokumentResultatSet.Companion.logResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JournalførDokumentServiceImpl(
    private val dokArkiv: DokArkiv,
    private val dokumentRepo: DokumentRepo,
    private val sakService: SakService,
    private val personService: PersonService,
) : JournalførDokumentService {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun journalfør() {
        val dokumenterSomMåJournalføres = dokumentRepo.hentDokumenterForJournalføring()

        dokumenterSomMåJournalføres.map { dokumentdistribusjon ->
            journalførDokument(dokumentdistribusjon)
                .map { DokumentResultatSet.Ok(dokumentdistribusjon.id) }
                .mapLeft { DokumentResultatSet.Feil(dokumentdistribusjon.id) }
        }.logResultat(log)
    }

    /**
     * Henter Person fra PersonService med systembruker.
     * Ment brukt fra async-operasjoner som ikke er knyttet til en bruker med token.
     *
     * Internal for testing.
     * sikkert fordi man ikke vil skrive så mye :shrug: kan bli gjort private hvis man tester mulige feil-caser
     */
    internal fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
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
            .mapLeft {
                when (it) {
                    is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført -> return dokumentdistribusjon.right()
                    KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring -> KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
                }
            }
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
