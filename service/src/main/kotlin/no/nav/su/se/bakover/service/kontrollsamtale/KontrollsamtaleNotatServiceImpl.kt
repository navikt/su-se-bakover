package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatClient
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatCommand
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfGenerator
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollnotatPdfInnhold
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatRepo
import no.nav.su.se.bakover.domain.kontrollnotat.kontrollnotatInnhold.KontrollnotatInnhold
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import person.domain.Person
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class KontrollsamtaleNotatServiceImpl(
    private val sakService: SakService,
    private val personService: PersonService,
    private val repository: KontrollsamtaleNotatRepo,
    private val pdfGenerator: PdfGenerator,
    private val clock: Clock,
    private val journalførKontrollnotatClient: JournalførKontrollnotatClient,

) : KontrollsamtaleNotatService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(
        sakId: UUID,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sessionContext: SessionContext?,
    ): Either<KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost, KontrollsamtaleNotat> {
        val sakInfo = sakService.hentSakInfo(sakId).getOrElse {
            log.error("Kunne ikke hente sak for å opprette journalpost. Originalfeil: $it")
            return KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost(
                sakId = sakId,
                kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                grunn = "Kunne ikke hente sak for å opprette journalpost",
            ).left()
        }

        val person = personService.hentPerson(
            fnr = sakInfo.fnr,
            sakstype = sakInfo.type,
        ).getOrElse {
            log.error("Kunne ikke hente person for å opprette journalpost. Originalfeil: $it")
            return KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost(
                sakId = sakId,
                kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                grunn = "Kunne ikke hente person for å opprette journalpost",
            ).left()
        }

        val journalpostId = opprettJournalpost(
            sakInfo = sakInfo,
            kontrollsamtaleNotat = kontrollsamtaleNotat,
            person = person,
        ).getOrElse {
            log.error("Kunne ikke opprette journalpost. Originalfeil: $it")
            return it.left()
        }

        val kontrollsamtaleNotatMedJournalpost = kontrollsamtaleNotat.copy(
            journalpostId = journalpostId,
        )

        repository.lagre(
            kontrollsamtaleNotat = kontrollsamtaleNotatMedJournalpost,
            sakId = sakId,
            sessionContext = sessionContext,
        )
        return kontrollsamtaleNotatMedJournalpost.right()
    }

    override fun hentKontrollsamtaleNotat(sakId: UUID): Either<KontrollsamtaleNotatService.FantIkkeKontrollnotat, KontrollsamtaleNotat> {
        return repository.hentKontrollsamtaleNotat(sakId)?.right()
            ?: KontrollsamtaleNotatService.FantIkkeKontrollnotat.left()
    }

    override fun hentSakIdForKontrollsamtaleNotat(kontrollsamtaleNotatId: UUID): UUID? {
        return repository.hentSakIdForKontrollsamtaleNotat(kontrollsamtaleNotatId)
    }

    override fun hentKontrollsamtaleNotatPdf(sakId: UUID): Either<KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf, PdfA> {
        return sakService.hentSak(sakId).mapLeft {
            log.error("Hent kontrollnotat-PDF: Fant ikke sak")
            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeSak
        }.flatMap { sak ->
            personService.hentPerson(sak.fnr, sakstype = sak.type).mapLeft {
                log.error("Hent kontrollnotat-PDF: Fant ikke person")
                KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkePerson
            }.flatMap { person ->
                hentKontrollsamtaleNotat(sakId).mapLeft {
                    log.error("Hent kontrollnotat-PDF: Fant ikke kontrollnotat")
                    KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeKontrollnotat
                }.flatMap { kontrollnotat ->
                    val pdfInnhold = KontrollnotatPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        sakstype = sak.type,
                        navn = person.navn,
                        kontrollnotat = KontrollnotatInnhold(
                            personligOppmøte = kontrollnotat.personligOppmøte,
                            fullmaktOgLegeerklæring = kontrollnotat.fullmaktOgLegeerklæring,
                            originalPass = kontrollnotat.originalPass,
                            gyldigPass = kontrollnotat.gyldigPass,
                            harVærtUtenlands = kontrollnotat.harVærtUtenlands,
                            utenlandsoppholdDatoer = kontrollnotat.utenlandsoppholdDatoer.map {
                                "${it.utreiseDato} - ${it.innreiseDato}"
                            },
                            harPlanerOmUtenlandsreise = kontrollnotat.harPlanerOmUtenlandsreise,
                            planlagteUtenlandsreiseDatoer = kontrollnotat.planlagteUtenlandsreiseDatoer.map {
                                "${it.utreiseDato} - ${it.innreiseDato}"
                            },
                            reiseDokumentasjon = kontrollnotat.reiseDokumentasjon,
                            økonomiskSituasjon = kontrollnotat.økonomiskSituasjon,
                            andreForhold = kontrollnotat.andreForhold,
                            skatteOpplysninger = kontrollnotat.skatteOpplysninger,
                            fritekst = kontrollnotat.fritekst,
                        ),
                        clock = clock,
                    )
                    pdfGenerator.genererPdf(
                        pdfInnhold,
                    )
                }
                    .mapLeft {
                        log.error("Hent kontrollnotat-PDF: Kunne ikke generere PDF. Originalfeil: $it")
                        KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeLagePdf
                    }
            }
        }
    }

    override fun opprettJournalpost(
        sakInfo: SakInfo,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        person: Person,
    ): Either<KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost, JournalpostId> {
        val pdf = pdfGenerator.genererPdf(
            KontrollnotatPdfInnhold.create(
                saksnummer = sakInfo.saksnummer,
                sakstype = sakInfo.type,
                navn = person.navn,
                kontrollnotat = KontrollnotatInnhold(
                    personligOppmøte = kontrollsamtaleNotat.personligOppmøte,
                    fullmaktOgLegeerklæring = kontrollsamtaleNotat.fullmaktOgLegeerklæring,
                    originalPass = kontrollsamtaleNotat.originalPass,
                    gyldigPass = kontrollsamtaleNotat.gyldigPass,
                    harVærtUtenlands = kontrollsamtaleNotat.harVærtUtenlands,
                    utenlandsoppholdDatoer = kontrollsamtaleNotat.utenlandsoppholdDatoer.map {
                        "${it.utreiseDato} - ${it.innreiseDato}"
                    },
                    harPlanerOmUtenlandsreise = kontrollsamtaleNotat.harPlanerOmUtenlandsreise,
                    planlagteUtenlandsreiseDatoer = kontrollsamtaleNotat.planlagteUtenlandsreiseDatoer.map {
                        "${it.utreiseDato} - ${it.innreiseDato}"
                    },
                    reiseDokumentasjon = kontrollsamtaleNotat.reiseDokumentasjon,
                    økonomiskSituasjon = kontrollsamtaleNotat.økonomiskSituasjon,
                    andreForhold = kontrollsamtaleNotat.andreForhold,
                    skatteOpplysninger = kontrollsamtaleNotat.skatteOpplysninger,
                    fritekst = kontrollsamtaleNotat.fritekst,
                ),
                clock = clock,
            ),
        ).getOrElse {
            log.error("Kunne ikke generere PDF. Originalfeil: $it")
            return KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost(
                sakId = kontrollsamtaleNotat.id,
                kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                grunn = "Kunne ikke generere PDF",
            ).left()
        }
        log.info("Ny søknad: Generert PDF ok.")
        return journalførKontrollnotatClient.journalførKontrollnotat(
            command = JournalførKontrollnotatCommand(
                sakstype = sakInfo.type,
                saksnummer = sakInfo.saksnummer,
                fnr = person.ident.fnr,
                kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                tittel = "Kontrollnotat",
                kontrollnotatJson = serialize(kontrollsamtaleNotat),
                kontrollnotatPdf = pdf,
                datoDokument = kontrollsamtaleNotat.opprettet,
            ),
        ).mapLeft {
            log.error("Kunne ikke opprette journalpost. Originalfeil: $it")
            KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost(
                sakId = kontrollsamtaleNotat.id,
                kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                grunn = "Kunne ikke opprette journalpost",
            )
        }
    }
}
