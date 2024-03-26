package no.nav.su.se.bakover.kontrollsamtale.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.command.InnkallingTilKontrollsamtaleDokumentCommand
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeKalleInnTilKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeSetteNyDatoForKontrollsamtale
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class KontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : KontrollsamtaleService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun kallInn(
        sakId: UUID,
        kontrollsamtale: Kontrollsamtale,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit> {
        val sak = sakService.hentSak(sakId).getOrElse {
            log.error("Fant ikke sak for sakId $sakId")
            return KunneIkkeKalleInnTilKontrollsamtale.FantIkkeSak.left()
        }

        val gjeldendeStønadsperiode = sak.hentGjeldendeStønadsperiode(clock)
            ?: return KunneIkkeKalleInnTilKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left().also {
                log.error("Fant ingen gjeldende stønadsperiode på sakId $sakId")
            }

        val dokument = lagDokument(fødselsnummer = sak.fnr, sakId = sakId, saksnummer = sak.saksnummer).getOrElse {
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
            log.error("Fant ikke sak for sakId $sakId")
            return KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeSak.left()
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
                kontrollsamtale.endreDato(dato).map { endretKontrollsamtale ->
                    kontrollsamtaleRepo.lagre(endretKontrollsamtale)
                }.mapLeft {
                    when (it) {
                        Kontrollsamtale.KunneIkkeEndreDato.DatoErIkkeFørsteIMåned -> KunneIkkeSetteNyDatoForKontrollsamtale.DatoIkkeFørsteIMåned
                        Kontrollsamtale.KunneIkkeEndreDato.UgyldigStatusovergang -> KunneIkkeSetteNyDatoForKontrollsamtale.UgyldigStatusovergang
                    }
                }
            },
        )
    }

    override fun hentNestePlanlagteKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext,
    ): Either<KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale, Kontrollsamtale> {
        val samtaler = kontrollsamtaleRepo.hentForSakId(sakId, sessionContext).sortedBy { it.innkallingsdato }
        // TODO jah: Dette kunne vi filtrert i databasen og dersom vi brukte sterkere typer, kunne vi returnert en mer eksakt type.
        return samtaler.find { it.status == Kontrollsamtalestatus.PLANLAGT_INNKALLING }?.right()
            ?: KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale.left()
    }

    override fun hentKontrollsamtaler(sakId: UUID): List<Kontrollsamtale> {
        return kontrollsamtaleRepo.hentForSakId(sakId)
    }

    override fun hentPlanlagteKontrollsamtaler(
        sessionContext: SessionContext,
    ): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>> {
        return Either.catch { kontrollsamtaleRepo.hentAllePlanlagte(LocalDate.now(clock), sessionContext) }.mapLeft {
            log.error("Kunne ikke hente planlagte kontrollsamtaler før ${LocalDate.now(clock)}", it)
            return KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler.left()
        }
    }

    override fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate? {
        return kontrollsamtaleRepo.hentFristUtløptFørEllerPåDato(fristFørEllerPåDato)
    }

    override fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): List<Kontrollsamtale> {
        return kontrollsamtaleRepo.hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato)
    }

    private fun lagDokument(
        fødselsnummer: Fnr,
        sakId: UUID,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument, Dokument.MedMetadata> {
        val brevCommand = InnkallingTilKontrollsamtaleDokumentCommand(
            fødselsnummer = fødselsnummer,
            saksnummer = saksnummer,
        )
        return brevService.lagDokument(command = brevCommand)
            .mapLeft {
                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument(it)
            }
            .map {
                it.leggTilMetadata(
                    metadata = Dokument.Metadata(sakId = sakId),
                )
            }
    }

    override fun defaultSessionContext(): SessionContext = sessionFactory.newSessionContext()

    /**
     * Brukt fra komponenttester.
     */
    fun hentForSak(sakId: UUID): List<Kontrollsamtale> {
        return kontrollsamtaleRepo.hentForSakId(sakId)
    }

    override fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext) {
        kontrollsamtaleRepo.lagre(kontrollsamtale, sessionContext)
    }
}
