package no.nav.su.se.bakover.kontrollsamtale.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import dokument.domain.journalføring.QueryJournalpostClient
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.InnkallingTilKontrollsamtaleDokumentCommand
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeKalleInnTilKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeSetteNyDatoForKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.annuller.KunneIkkeAnnullereKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.hent.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.OppdaterInnkallingsmånedPåKontrollsamtaleCommand
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.oppdaterInnkallingsmånedPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.KunneIkkeOppdatereStatusPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.OppdaterStatusPåKontrollsamtaleCommand
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.oppdaterStatusPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.KanIkkeOppretteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.OpprettKontrollsamtaleCommand
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.opprettKontrollsamtale
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class KontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
    private val personService: PersonService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val queryJournalpostClient: QueryJournalpostClient,
) : KontrollsamtaleService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun kallInnTilKontrollsamtale(
        kontrollsamtale: Kontrollsamtale,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit> {
        val sakId = kontrollsamtale.sakId
        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalArgumentException("Fant ikke sak for sakId $sakId")
        }

        val gjeldendeStønadsperiode = sak.hentGjeldendeStønadsperiode(clock)
            ?: return KunneIkkeKalleInnTilKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left().also {
                log.error("Fant ingen gjeldende stønadsperiode på sakId $sakId")
            }

        if (kontrollsamtale.innkallingsdato >= gjeldendeStønadsperiode.tilOgMed) {
            log.info("Sak er opphørt ved innkallingsdato for sakId $sakId, saksnummer ${sak.saksnummer}. Avbryter innkalling til kontrollsamtale.")
            kontrollsamtaleRepo.lagre(
                kontrollsamtale = kontrollsamtale.annuller().getOrElse {
                    log.error("Kontrollsamtale er i ugyldig tilstand for å annulleres: $kontrollsamtale")
                    return KunneIkkeKalleInnTilKontrollsamtale.UgyldigTilstand.left()
                },
            )
            return KunneIkkeKalleInnTilKontrollsamtale.SakErOpphørt.left()
        }

        val person = personService.hentPersonMedSystembruker(sak.fnr).getOrElse {
            log.error("Fant ikke person for sakId $sakId, saksnummer ${sak.saksnummer}")
            return KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson.left()
        }

        if (person.erDød()) {
            log.info("Person er død for sakId $sakId, saksnummer ${sak.saksnummer}. Avbryter innkalling til kontrollsamtale.")
            kontrollsamtaleRepo.lagre(
                kontrollsamtale = kontrollsamtale.annuller().getOrElse {
                    log.error("Kontrollsamtale er i ugyldig tilstand for å annulleres: $kontrollsamtale")
                    return KunneIkkeKalleInnTilKontrollsamtale.UgyldigTilstand.left()
                },
            )
            return KunneIkkeKalleInnTilKontrollsamtale.PersonErDød.left()
        }

        if (sak.erStanset()) {
            log.info("Sak er stanset for sakId $sakId, saksnummer ${sak.saksnummer}. Venter med å kalle inn til kontrollsamtale.")
            return KunneIkkeKalleInnTilKontrollsamtale.SakErStanset.left()
        }

        val dokument = lagDokument(sak).getOrElse {
            log.error("Klarte ikke lage dokument for innkalling til kontrollsamtale på sakId $sakId")
            return it.left()
        }

        return Either.catch {
            sessionFactory.withTransactionContext { transactionContext ->
                brevService.lagreDokument(dokument, transactionContext)
                kontrollsamtaleRepo.lagre(
                    kontrollsamtale = kontrollsamtale.settInnkalt(dokument.id).getOrElse {
                        throw RuntimeException("Kontrollsamtale er i ugyldig tilstand for å bli satt til innkalt")
                    },
                    sessionContext = transactionContext,
                )
                Kontrollsamtale.opprettNyKontrollsamtale(gjeldendeStønadsperiode, sakId, clock).map {
                    kontrollsamtaleRepo.lagre(it, transactionContext)
                }
            }
            oppgaveService.opprettOppgaveMedSystembruker(
                config = OppgaveConfig.Kontrollsamtale(
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    clock = clock,
                    sakstype = sak.type,
                ),
            ).getOrElse {
                throw RuntimeException("Fikk ikke opprettet oppgave").also {
                    log.error("Fikk ikke opprettet oppgave")
                }
            }
        }.fold(
            ifLeft = {
                log.error("Klarte ikke kalle inn kontrollsamtale for sakId $sakId", it)
                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn.left()
            },
            ifRight = {
                log.info("Kontrollsamtale kalt inn for sakId $sakId")
                Unit.right()
            },
        )
    }

    override fun nyDato(sakId: UUID, dato: LocalDate): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit> {
        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalArgumentException("Fant ikke sak for sakId $sakId")
        }
        sak.harGjeldendeEllerFremtidigStønadsperiode(clock).ifFalse {
            log.info("Fant ingen gjeldende stønadsperiode på sakId $sakId")
            return KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left()
        }

        return hentNestePlanlagteKontrollsamtale(sakId).fold(
            ifLeft = {
                kontrollsamtaleRepo.lagre(
                    Kontrollsamtale.opprettNyKontrollsamtale(
                        sakId = sakId,
                        innkallingsdato = dato,
                        clock = clock,
                    ),
                ).right()
            },
            ifRight = { kontrollsamtale ->
                kontrollsamtale.oppdaterInnkallingsdato(dato).map { endretKontrollsamtale ->
                    kontrollsamtaleRepo.lagre(endretKontrollsamtale)
                }.mapLeft {
                    when (it) {
                        Kontrollsamtale.KunneIkkeOppdatereDato.DatoErIkkeFørsteIMåned -> KunneIkkeSetteNyDatoForKontrollsamtale.DatoIkkeFørsteIMåned
                        Kontrollsamtale.KunneIkkeOppdatereDato.UgyldigStatusovergang -> KunneIkkeSetteNyDatoForKontrollsamtale.UgyldigStatusovergang
                    }
                }
            },
        )
    }

    override fun hentNestePlanlagteKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): Either<KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale, Kontrollsamtale> {
        val samtaler = kontrollsamtaleRepo.hentForSakId(sakId, sessionContext).sortedBy { it.innkallingsdato }
        return samtaler.find { it.status == Kontrollsamtalestatus.PLANLAGT_INNKALLING }?.right()
            ?: KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale.left()
    }

    override fun hentKontrollsamtaler(sakId: UUID): Kontrollsamtaler {
        return kontrollsamtaleRepo.hentForSakId(sakId)
    }

    /** Her kan vi ikke bruke [Kontrollsamtaler], siden den er begrenset til en sak. */
    override fun hentPlanlagteKontrollsamtaler(
        sessionContext: SessionContext?,
    ): List<Kontrollsamtale> {
        return kontrollsamtaleRepo.hentAllePlanlagte(LocalDate.now(clock), sessionContext)
    }

    override fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate? {
        return kontrollsamtaleRepo.hentFristUtløptFørEllerPåDato(fristFørEllerPåDato)
    }

    /** Her kan vi ikke bruke [Kontrollsamtaler], siden den er begrenset til en sak. */
    override fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): List<Kontrollsamtale> {
        return kontrollsamtaleRepo.hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato)
    }

    private fun lagDokument(sak: Sak): Either<KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument, Dokument.MedMetadata> {
        val brevCommand = InnkallingTilKontrollsamtaleDokumentCommand(
            fødselsnummer = sak.fnr,
            saksnummer = sak.saksnummer,
            sakstype = sak.type,
        )
        return brevService.lagDokument(command = brevCommand)
            .mapLeft {
                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument(it)
            }
            .map {
                it.leggTilMetadata(
                    metadata = Dokument.Metadata(sakId = sak.id),
                    // kan ikke sende kontrollsamtale brevet til en annen adresse enn brukerens adresse per nå
                    distribueringsadresse = null,
                )
            }
    }

    /**
     * Brukt fra komponenttester.
     */
    fun hentForSak(sakId: UUID): Kontrollsamtaler {
        return kontrollsamtaleRepo.hentForSakId(sakId)
    }

    override fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext?) {
        kontrollsamtaleRepo.lagre(kontrollsamtale, sessionContext)
    }

    override fun annullerKontrollsamtale(
        sakId: UUID,
        kontrollsamtaleId: UUID,
        sessionContext: SessionContext?,
    ): Either<KunneIkkeAnnullereKontrollsamtale, Kontrollsamtale> {
        val kontrollsamtale = kontrollsamtaleRepo.hentForKontrollsamtaleId(kontrollsamtaleId)
            ?: throw IllegalArgumentException("Fant ikke kontrollsamtale. KontrollsamtaleId: $kontrollsamtaleId. SakId: $sakId.")

        return kontrollsamtale.annuller().mapLeft {
            log.error("Kunne ikke annullere kontrollsamtale ${kontrollsamtale.id} med status ${kontrollsamtale.status}. SakId: $sakId.")
            KunneIkkeAnnullereKontrollsamtale.UgyldigStatusovergang
        }.onRight {
            kontrollsamtaleRepo.lagre(it, sessionContext)
        }
    }

    override fun opprettKontrollsamtale(
        command: OpprettKontrollsamtaleCommand,
        sessionContext: SessionContext?,
    ): Either<KanIkkeOppretteKontrollsamtale, Kontrollsamtale> {
        val kontrollsamtaler = kontrollsamtaleRepo.hentForSakId(command.sakId)
        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalArgumentException("Kunne ikke opprette kontrollsamtale. Fant ikke sak. Command=$command")
        }
        return sak.opprettKontrollsamtale(
            command = command,
            eksisterendeKontrollsamtalerForSak = kontrollsamtaler,
            clock = clock,
        ).map { (kontrollsamtale, _) ->
            kontrollsamtaleRepo.lagre(kontrollsamtale, sessionContext)
            kontrollsamtale
        }
    }

    override fun oppdaterInnkallingsmånedPåKontrollsamtale(
        command: OppdaterInnkallingsmånedPåKontrollsamtaleCommand,
        sessionContext: SessionContext?,
    ): Either<KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale, Kontrollsamtale> {
        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalArgumentException("Kunne ikke oppdatere innkallingsmåned på kontrollsamtale. Fant ikke sak. Command=$command")
        }
        val kontrollsamtaler = kontrollsamtaleRepo.hentForSakId(command.sakId)

        return sak.oppdaterInnkallingsmånedPåKontrollsamtale(
            command = command,
            kontrollsamtaler = kontrollsamtaler,
            clock = clock,
        ).map {
            kontrollsamtaleRepo.lagre(it.first, sessionContext)
            it.first
        }
    }

    override fun oppdaterStatusPåKontrollsamtale(
        command: OppdaterStatusPåKontrollsamtaleCommand,
        sessionContext: SessionContext?,
    ): Either<KunneIkkeOppdatereStatusPåKontrollsamtale, Kontrollsamtale> {
        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalArgumentException("Kunne ikke oppdatere status på kontrollsamtale. Fant ikke sak. Command=$command")
        }
        val kontrollsamtaler = kontrollsamtaleRepo.hentForSakId(command.sakId)

        return runBlocking {
            sak.oppdaterStatusPåKontrollsamtale(
                command = command,
                kontrollsamtaler = kontrollsamtaler,
                erJournalpostTilknyttetSak = queryJournalpostClient::erTilknyttetSak,
            ).map {
                kontrollsamtaleRepo.lagre(it.first, sessionContext)
                it.first
            }
        }
    }
}
