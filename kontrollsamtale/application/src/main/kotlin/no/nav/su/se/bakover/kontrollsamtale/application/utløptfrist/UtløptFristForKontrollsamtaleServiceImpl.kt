package no.nav.su.se.bakover.kontrollsamtale.application.utløptfrist

import arrow.core.Either
import arrow.core.getOrElse
import dokument.domain.journalføring.ErKontrollNotatMottatt
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.tid.førsteINesteMåned
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleJobRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import org.slf4j.LoggerFactory
import person.domain.PersonService
import økonomi.domain.utbetaling.UtbetalingFeilet
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class UtløptFristForKontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val queryJournalpostClient: QueryJournalpostClient,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val stansAvYtelseService: StansYtelseService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val serviceUser: String,
    private val oppgaveService: OppgaveService,
    private val kontrollsamtaleJobRepo: KontrollsamtaleJobRepo,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
    private val personService: PersonService,
) : UtløptFristForKontrollsamtaleService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private fun opprettStans(
        sakId: UUID,
        stansDato: LocalDate,
        transactionContext: TransactionContext,
    ): StansAvYtelseRevurdering.SimulertStansAvYtelse {
        return stansAvYtelseService.stansAvYtelseITransaksjon(
            StansYtelseRequest.Opprett(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler(serviceUser),
                fraOgMed = stansDato,
                revurderingsårsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create(
                        value = "Automatisk stanset som følge av manglende oppmøte til kontrollsamtale",
                    ),
                ),
            ),
            transactionContext = transactionContext,
        )
    }

    private fun sjekkOmPersonLeverOgManSkalHåndtere(
        sak: Sak,
        kontrollsamtale: Kontrollsamtale,
    ): Boolean {
        val person = personService.hentPersonMedSystembruker(sak.fnr).getOrElse {
            log.error("Fant ikke person for sakId ${sak.id}, saksnummer ${sak.saksnummer}")
            return false
        }

        if (person.erDød()) {
            log.info("Person er død for sakId ${sak.id}, saksnummer ${sak.saksnummer}. Avbryter oppfølging kontrollsamtale.")
            val annullert = kontrollsamtale.annuller().getOrElse {
                throw IllegalStateException("Kunne ikke annullere kontrollsamtale ${kontrollsamtale.id}, sakId ${sak.id}, saksnummer ${sak.saksnummer}")
            }
            sessionFactory.withTransactionContext { tx ->
                kontrollsamtaleRepo.lagre(annullert, tx)
                opprettStans(kontrollsamtale.sakId, LocalDate.now(clock), tx)
            }
            return false
        }
        return true
    }
    override fun stansStønadsperioderHvorKontrollsamtaleHarUtløptFrist(): UtløptFristForKontrollsamtaleContext? {
        val fristPåDato = kontrollsamtaleService.hentFristUtløptFørEllerPåDato(LocalDate.now(clock))
            ?: run {
                log.info("Det finnes ingen kontrollsamtaler. Avslutter jobb.")
                return null
            }
        // Oppretter en tom jobb dersom den ikke finnes. Denne e
        val initialContext = hentEllerOpprettContext(fristPåDato)

        val innkalteKontrollsamtalerMedUtløptFrist =
            kontrollsamtaleService.hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato)
        return initialContext.uprosesserte(innkalteKontrollsamtalerMedUtløptFrist.map { it.id })
            .ifEmpty {
                log.debug("Fant ingen uprosesserte kontrollsamtaler med utløp: {}.", fristPåDato)
                return initialContext
            }
            .also {
                log.debug("Fant {} uprosesserte kontrollsamtaler med utløp: {}", it.count(), fristPåDato)
            }
            .fold(initialContext) { context, prosesseres ->
                val kontrollsamtale = innkalteKontrollsamtalerMedUtløptFrist.single { it.id == prosesseres }
                val sak = sakService.hentSak(kontrollsamtale.sakId).getOrElse {
                    throw IllegalStateException("fant ikke sak for kontrollsamtale ${kontrollsamtale.id}, sakId ${kontrollsamtale.sakId}")
                }
                if (!sjekkOmPersonLeverOgManSkalHåndtere(sak, kontrollsamtale)) {
                    return@fold context
                }
                håndterKontrollsamtale(
                    context = context,
                    kontrollsamtale = kontrollsamtale,
                    sak = sak,
                )
            }
            .also {
                log.info(it.oppsummering(clock))
            }
    }

    private fun håndterKontrollsamtale(
        context: UtløptFristForKontrollsamtaleContext,
        kontrollsamtale: Kontrollsamtale,
        sak: Sak,
    ): UtløptFristForKontrollsamtaleContext {
        return Either.catch {
            queryJournalpostClient.kontrollnotatMotatt(
                sak.saksnummer,
                kontrollsamtale.forventetMottattKontrollnotatIPeriode(),
            ).mapLeft {
                KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
            }.fold(
                {
                    throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil)
                },
                { erKontrollnotatMottatt ->
                    sessionFactory.withTransactionContext { tx ->
                        when (erKontrollnotatMottatt) {
                            is ErKontrollNotatMottatt.Ja -> {
                                håndterMøttTilKontrollsamtale(
                                    context = context,
                                    kontrollsamtale = kontrollsamtale,
                                    erKontrollnotatMottatt = erKontrollnotatMottatt,
                                    tx = tx,
                                )
                            }

                            is ErKontrollNotatMottatt.Nei -> {
                                håndterIkkeMøttTilKontrollsamtale(
                                    context = context,
                                    kontrollsamtale = kontrollsamtale,
                                    tx = tx,
                                )
                            }
                        }
                    }
                },
            )
        }.fold(
            { error ->
                Either.catch {
                    sessionFactory.withTransactionContext { tx ->
                        håndterFeil(
                            context = context,
                            kontrollsamtale = kontrollsamtale,
                            error = error,
                            sakInfo = sak.info(),
                            tx = tx,
                        )
                    }
                }.fold(
                    {
                        log.error(
                            "Feil: ${it.message} ved håndtering av feilet kontrollsamtale: ${kontrollsamtale.id}",
                            it,
                        )
                        context
                    },
                    {
                        log.info(
                            "Feil: ${error.message} ved prosessering av kontrollsamtale: ${kontrollsamtale.id}",
                            it,
                        )
                        it
                    },
                )
            },
            {
                it
            },
        )
    }

    private fun håndterIkkeMøttTilKontrollsamtale(
        context: UtløptFristForKontrollsamtaleContext,
        kontrollsamtale: Kontrollsamtale,
        tx: TransactionContext,
    ): UtløptFristForKontrollsamtaleContext {
        return kontrollsamtale.settIkkeMøttInnenFrist()
            .fold(
                {
                    throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                },
                { ikkeMøttKontrollsamtale ->
                    kontrollsamtaleService.lagre(
                        ikkeMøttKontrollsamtale,
                        tx,
                    )
                    val revurdering = opprettStans(
                        ikkeMøttKontrollsamtale.sakId,
                        ikkeMøttKontrollsamtale.frist.førsteINesteMåned(),
                        tx,
                    )
                    val oppdatertContext = context.ikkeMøtt(ikkeMøttKontrollsamtale.id, clock)

                    kontrollsamtaleJobRepo.lagre(oppdatertContext, tx)
                    try {
                        stansAvYtelseService.iverksettStansAvYtelseITransaksjon(
                            revurderingId = revurdering.id,
                            attestant = NavIdentBruker.Attestant(serviceUser),
                            transactionContext = tx,
                        )
                    } catch (_: Exception) {
                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = UtbetalingFeilet.Protokollfeil::class.java.toString())
                    }

                    oppdatertContext
                },
            )
    }

    private fun håndterMøttTilKontrollsamtale(
        context: UtløptFristForKontrollsamtaleContext,
        kontrollsamtale: Kontrollsamtale,
        erKontrollnotatMottatt: ErKontrollNotatMottatt.Ja,
        tx: TransactionContext,
    ): UtløptFristForKontrollsamtaleContext {
        return kontrollsamtale.settGjennomført(erKontrollnotatMottatt.kontrollnotat.journalpostId)
            .fold(
                {
                    throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                },
                { møttTilKontrollsamtale ->
                    kontrollsamtaleService.lagre(
                        møttTilKontrollsamtale,
                        tx,
                    )
                    context.prosessert(
                        møttTilKontrollsamtale.id,
                        clock,
                    ).also {
                        kontrollsamtaleJobRepo.lagre(
                            it,
                            tx,
                        )
                    }
                },
            )
    }

    private fun håndterFeil(
        context: UtløptFristForKontrollsamtaleContext,
        kontrollsamtale: Kontrollsamtale,
        error: Throwable,
        sakInfo: SakInfo,
        tx: TransactionContext,
    ): UtløptFristForKontrollsamtaleContext {
        return context.feilet(
            kontrollsamtale.id,
            error.message ?: error::class.java.toString(),
            clock,
        ).let { oppdatertContext ->
            if (context.retryLimitReached(kontrollsamtale.id)) {
                val oppgaveId = oppgaveService.opprettOppgaveMedSystembruker(
                    OppgaveConfig.KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale(
                        saksnummer = sakInfo.saksnummer,
                        periode = kontrollsamtale.forventetMottattKontrollnotatIPeriode(),
                        fnr = sakInfo.fnr,
                        clock = clock,
                        sakstype = sakInfo.type,
                    ),
                ).mapLeft {
                    KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                }.map {
                    it.oppgaveId
                }.getOrElse { throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil) }

                oppdatertContext.prosessertMedFeil(
                    kontrollsamtale.id,
                    clock,
                    oppgaveId,
                ).also {
                    log.info(
                        "Maks antall forsøk (${UtløptFristForKontrollsamtaleContext.MAX_RETRIES + 1}) for kontrollsamtale:${kontrollsamtale.id} nådd. Gir opp videre prosessering. OppgaveId: $oppgaveId opprettet.",
                        RuntimeException("Genererer stacktrace for enklere debugging."),
                    )
                    kontrollsamtaleJobRepo.lagre(
                        it,
                        tx,
                    )
                }
            } else {
                oppdatertContext.also {
                    kontrollsamtaleJobRepo.lagre(
                        oppdatertContext,
                        tx,
                    )
                }
            }
        }
    }

    private fun hentEllerOpprettContext(dato: LocalDate): UtløptFristForKontrollsamtaleContext {
        return kontrollsamtaleJobRepo.hent(
            UtløptFristForKontrollsamtaleContext.genererId(dato),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().name}, dato: ${it.id().date}")
        } ?: UtløptFristForKontrollsamtaleContext(
            id = UtløptFristForKontrollsamtaleContext.genererId(dato),
            clock = clock,
        ).also {
            log.info("Oppretter ny context for jobb: ${it.id().name}, dato: ${it.id().date}")
        }
    }

    private fun Kontrollsamtale.forventetMottattKontrollnotatIPeriode(): DatoIntervall {
        return DatoIntervall(this.innkallingsdato, this.frist)
    }

    private data class KunneIkkeHåndtereUtløptKontrollsamtale(val feil: String)

    private data class FeilVedProsesseringAvKontrollsamtaleException(val msg: String) : RuntimeException(msg)
}
