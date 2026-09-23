package no.nav.su.se.bakover.kontrollsamtale.application.kontrollnotat

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.PdfGenerator
import dokument.domain.forsteside.ForstesideGeneratorService
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatClient
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatCommand
import dokument.domain.journalføring.tilBehandlingstema
import dokument.domain.pdf.SammenslåPdf
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollnotatPdfInnhold
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatRepo
import no.nav.su.se.bakover.domain.kontrollnotat.kontrollnotatInnhold.KontrollnotatInnhold
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.kontrollsamtale.domain.kontrollnotat.KontrollsamtaleNotatService
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
    private val forstesideGeneratorService: ForstesideGeneratorService,
    private val clock: Clock,
    private val journalførKontrollnotatClient: JournalførKontrollnotatClient,
    private val oppgaveService: OppgaveService,
    private val kontrollsamtaleService: KontrollsamtaleService,
) : KontrollsamtaleNotatService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun harAktivKontrollsamtale(sakId: UUID): Boolean {
        return kontrollsamtaleService.hentKontrollsamtaler(sakId).any {
            it.status == Kontrollsamtalestatus.PLANLAGT_INNKALLING ||
                it.status == Kontrollsamtalestatus.INNKALT
        }
    }

    private sealed interface KunneIkkeGenererePdfMedForsteside {
        data object KunneIkkeGenererePdf : KunneIkkeGenererePdfMedForsteside
        data object KunneIkkeGenerereForside : KunneIkkeGenererePdfMedForsteside
        data object KunneIkkeSlåSammen : KunneIkkeGenererePdfMedForsteside
    }

    private fun genererKontrollnotatPdfMedForsteside(
        saksnummer: Saksnummer,
        sakstype: Sakstype,
        fnr: Fnr,
        navn: Person.Navn,
        kontrollnotat: KontrollsamtaleNotat,
    ): Either<KunneIkkeGenererePdfMedForsteside, PdfA> {
        val kontrollnotatPdf = pdfGenerator.genererPdf(
            KontrollnotatPdfInnhold.create(
                saksnummer = saksnummer,
                sakstype = sakstype,
                navn = navn,
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
            ),
        ).getOrElse {
            log.error("Kunne ikke generere kontrollnotat-PDF. Originalfeil: $it")
            return KunneIkkeGenererePdfMedForsteside.KunneIkkeGenererePdf.left()
        }

        val forsteside = forstesideGeneratorService.genererForKontrollnotat(
            brukerId = fnr.toString(),
            behandlingstema = sakstype.tilBehandlingstema(),
        ).getOrElse {
            log.error("Kunne ikke generere forside for kontrollnotat. Originalfeil: $it")
            return KunneIkkeGenererePdfMedForsteside.KunneIkkeGenerereForside.left()
        }

        return SammenslåPdf.slåsSammen(
            forsteside = forsteside.foersteside,
            dokument = kontrollnotatPdf,
        ).mapLeft {
            log.error("Kunne ikke slå sammen forside og kontrollnotat-PDF. Originalfeil: $it")
            KunneIkkeGenererePdfMedForsteside.KunneIkkeSlåSammen
        }
    }

    override fun lagre(
        sakId: UUID,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
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

        repository.lagre(
            kontrollsamtaleNotat = kontrollsamtaleNotat,
            sakId = sakId,
        )

        log.info("Forsøker opprette jorunapost for kontrollsamtaleNotat med id ${kontrollsamtaleNotat.id} sakid $sakId")
        opprettJournalpost(
            sakInfo = sakInfo,
            kontrollsamtaleNotat = kontrollsamtaleNotat,
            person = person,
        ).fold(
            ifLeft = {
                log.error(
                    "Kunne ikke opprette journalpost ved innsending av kontrollsamtale.kontrollsamtalenotatid ${kontrollsamtaleNotat.id} sakid $sakId  Originalfeil: $it, denne kjøres igjen i ForsøkJournalføringKontrollnotatJob senere",
                )
            },
            ifRight = { journalpostId ->
                log.info(
                    "Opprettet journalpost med id $journalpostId for kontrollsamtalenotat ${kontrollsamtaleNotat.id} sakid $sakId",
                )
                repository.oppdaterJournalpostId(
                    kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                    journalpostId = journalpostId,
                )

                if (!harAktivKontrollsamtale(sakId)) {
                    log.info("Kontrollsamtalenotat sendt inn uten at det finnes noen kontrollsamtale til inkalling på sakId $sakId. Oppretter Gosys-oppgave.")
                    oppgaveService.opprettOppgave(
                        OppgaveConfig.KontrollnotatUtenKontrollsamtale(
                            saksnummer = sakInfo.saksnummer,
                            fnr = sakInfo.fnr,
                            clock = clock,
                            sakstype = sakInfo.type,
                            journalpostId = journalpostId,
                        ),
                    ).onLeft {
                        log.error("Kunne ikke opprette Gosys-oppgave for kontrollsamtalenotat uten registrert kontrollsamtale til inkalling på sakId $sakId. Originalfeil: $it")
                    }
                }
            },
        )

        return kontrollsamtaleNotat.right()
    }

    override fun hentKontrollsamtaleNotat(sakId: UUID): Either<KontrollsamtaleNotatService.FantIkkeKontrollnotat, KontrollsamtaleNotat> {
        return repository.hentKontrollsamtaleNotat(sakId)?.right()
            ?: KontrollsamtaleNotatService.FantIkkeKontrollnotat.left()
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
                    genererKontrollnotatPdfMedForsteside(
                        saksnummer = sak.saksnummer,
                        sakstype = sak.type,
                        fnr = sak.fnr,
                        navn = person.navn,
                        kontrollnotat = kontrollnotat,
                    ).mapLeft {
                        when (it) {
                            KunneIkkeGenererePdfMedForsteside.KunneIkkeGenerereForside ->
                                KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeGenerereForside
                            KunneIkkeGenererePdfMedForsteside.KunneIkkeGenererePdf,
                            KunneIkkeGenererePdfMedForsteside.KunneIkkeSlåSammen,
                            -> KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeLagePdf
                        }
                    }
                }
            }
        }
    }

    override fun opprettJournalpost(
        sakInfo: SakInfo,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        person: Person,
    ): Either<KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost, JournalpostId> {
        val pdf = genererKontrollnotatPdfMedForsteside(
            saksnummer = sakInfo.saksnummer,
            sakstype = sakInfo.type,
            fnr = person.ident.fnr,
            navn = person.navn,
            kontrollnotat = kontrollsamtaleNotat,
        ).getOrElse {
            val grunn = when (it) {
                KunneIkkeGenererePdfMedForsteside.KunneIkkeGenererePdf -> "Kunne ikke generere PDF"
                KunneIkkeGenererePdfMedForsteside.KunneIkkeGenerereForside -> "Kunne ikke generere forside"
                KunneIkkeGenererePdfMedForsteside.KunneIkkeSlåSammen -> "Kunne ikke slå sammen forside og PDF"
            }
            log.error("Kunne ikke generere kontrollnotat-PDF med forside for kontrollsamtaleNotat med id ${kontrollsamtaleNotat.id} sakid ${kontrollsamtaleNotat.sakId}. Grunn: $grunn")
            return KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost(
                sakId = kontrollsamtaleNotat.sakId,
                kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                grunn = grunn,
            ).left()
        }
        log.info("Generert PDF med forside ok for kontrollsamtaleNotat med id ${kontrollsamtaleNotat.id} sakid ${kontrollsamtaleNotat.sakId}")

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
                sakId = kontrollsamtaleNotat.sakId,
                kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                grunn = "Kunne ikke opprette journalpost",
            )
        }
    }

    override fun forsøkJournalpostPåNytt() {
        repository.hentUtenJournalpostId()
            .forEach { kontrollsamtaleNotat ->

                val sakInfo = sakService.hentSakInfo(kontrollsamtaleNotat.sakId).getOrElse {
                    log.error("Kunne ikke hente sak for å opprette journalpost. Originalfeil: $it")
                    return@forEach
                }

                val person = personService.hentPersonMedSystembruker(
                    fnr = sakInfo.fnr,
                    sakstype = sakInfo.type,
                ).getOrElse {
                    log.error("Kunne ikke hente person for å opprette journalpost. Originalfeil: $it")
                    return@forEach
                }
                opprettJournalpost(
                    sakInfo = sakInfo,
                    kontrollsamtaleNotat = kontrollsamtaleNotat,
                    person = person,
                ).onLeft {
                    log.error("Kunne ikke opprette journalpost for kontrollsamtaleNotat med id ${kontrollsamtaleNotat.id}. Originalfeil: $it")
                }.onRight { journalpostId ->
                    log.info("Opprettet journalpost id $journalpostId for kontrollsamtaleNotat med id ${kontrollsamtaleNotat.id} sakid ${kontrollsamtaleNotat.sakId}")
                    repository.oppdaterJournalpostId(
                        kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
                        journalpostId = journalpostId,
                    )
                    if (!harAktivKontrollsamtale(kontrollsamtaleNotat.sakId)) {
                        oppgaveService.opprettOppgaveMedSystembruker(
                            OppgaveConfig.KontrollnotatUtenKontrollsamtale(
                                saksnummer = sakInfo.saksnummer,
                                fnr = sakInfo.fnr,
                                clock = clock,
                                sakstype = sakInfo.type,
                                journalpostId = journalpostId,
                            ),
                        ).onLeft {
                            log.error("Kunne ikke opprette Gosys-oppgave for kontrollsamtalenotat uten registrert kontrollsamtale på sakId ${kontrollsamtaleNotat.sakId}. Originalfeil: $it")
                        }
                    }
                }
            }
    }
}
