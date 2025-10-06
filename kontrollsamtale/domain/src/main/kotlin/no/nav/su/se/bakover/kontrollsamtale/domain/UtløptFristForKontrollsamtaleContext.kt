package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.Either
import arrow.core.getOrElse
import dokument.domain.journalføring.ErKontrollNotatMottatt
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.job.JobContext
import no.nav.su.se.bakover.common.domain.job.NameAndLocalDateId
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.tid.førsteINesteMåned
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import org.slf4j.LoggerFactory
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class UtløptFristForKontrollsamtaleContext(
    private val id: NameAndLocalDateId,
    private val opprettet: Tidspunkt,
    private val endret: Tidspunkt,
    private val prosessert: Set<UUID>,
    private val ikkeMøtt: Set<UUID>,
    private val feilet: Set<Feilet>,
) : JobContext {
    private val logger = LoggerFactory.getLogger(this::class.java)

    constructor(
        clock: Clock,
        id: NameAndLocalDateId,
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        endret: Tidspunkt = opprettet,
        prosessert: Set<UUID> = emptySet(),
        ikkeMøtt: Set<UUID> = emptySet(),
        feilet: Set<Feilet> = emptySet(),
    ) : this(id, opprettet, endret, prosessert, ikkeMøtt, feilet)

    override fun id(): NameAndLocalDateId {
        return id
    }

    fun opprettet(): Tidspunkt {
        return opprettet
    }

    fun endret(): Tidspunkt {
        return endret
    }

    fun prosessert(id: UUID, clock: Clock): UtløptFristForKontrollsamtaleContext {
        val fjernHvisFeilet = feilet.find { it.id == id }?.let { feilet.minus(it) } ?: feilet
        return copy(prosessert = prosessert + id, feilet = fjernHvisFeilet, endret = Tidspunkt.now(clock))
    }

    fun prosessert(): Set<UUID> {
        return prosessert
    }

    fun ikkeMøtt(id: UUID, clock: Clock): UtløptFristForKontrollsamtaleContext {
        val fjernHvisFeilet = feilet.find { it.id == id }?.let { feilet.minus(it) } ?: feilet
        return copy(
            prosessert = prosessert + id,
            ikkeMøtt = ikkeMøtt + id,
            feilet = fjernHvisFeilet,
            endret = Tidspunkt.now(clock),
        )
    }

    fun ikkeMøtt(): Set<UUID> {
        return ikkeMøtt
    }

    fun feilet(id: UUID, feil: String, clock: Clock): UtløptFristForKontrollsamtaleContext {
        return feilet.find { it.id == id }?.let {
            copy(feilet = feilet.minus(it) + it.retried(feil), endret = Tidspunkt.now(clock))
        } ?: copy(feilet = feilet + Feilet(id, 0, feil, null), endret = Tidspunkt.now(clock))
    }

    fun retryLimitReached(id: UUID): Boolean {
        return feilet.find { it.id == id }?.let { it.retries >= MAX_RETRIES } ?: false
    }

    fun prosessertMedFeil(id: UUID, clock: Clock, oppgaveId: OppgaveId): UtløptFristForKontrollsamtaleContext {
        return feilet.find { it.id == id }!!.let {
            copy(
                prosessert = prosessert + id,
                feilet = feilet.minus(it) + it.copy(oppgaveId = oppgaveId.toString()),
                endret = Tidspunkt.now(clock),
            )
        }
    }

    fun møtt(): Set<UUID> {
        return prosessert() - ikkeMøtt() - feilet().map { it.id }.toSet()
    }

    fun feilet(): Set<Feilet> {
        return feilet
    }

    data class Feilet(
        val id: UUID,
        val retries: Int,
        val feil: String,
        val oppgaveId: String?,
    ) {
        fun retried(feil: String): Feilet {
            return copy(retries = retries + 1, feil = feil)
        }
    }

    fun uprosesserte(utestående: () -> List<UUID>): Set<UUID> {
        return (utestående().toSet()).minus(prosessert())
    }

    fun oppsummering(clock: Clock): String {
        return """
            ${"\n"}
            ***********************************
            Oppsummering av jobb: ${id.name}, tidspunkt:${Tidspunkt.now(clock)},
            Frist utløpt: ${id.date},
            Opprettet: $opprettet,
            Endret: $endret,
            Prosessert: $prosessert,
            Møtt: ${møtt()},
            IkkeMøtt: $ikkeMøtt,
            Feilet: $feilet
            ***********************************
            ${"\n"}
        """.trimIndent()
    }

    fun håndter(
        kontrollsamtale: Kontrollsamtale,
        sak: Sak,
        hentKontrollnotatMottatt: (saksnummer: Saksnummer, periode: DatoIntervall) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, ErKontrollNotatMottatt>,
        sessionFactory: SessionFactory,
        opprettStans: (sakId: UUID, fraOgMed: LocalDate, transactionContext: TransactionContext) -> OpprettStansTransactionCallback,
        iverksettStans: (id: RevurderingId, transactionContext: TransactionContext) -> IverksettStansTransactionCallback,
        lagreContext: (context: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext) -> Unit,
        clock: Clock,
        lagreKontrollsamtale: (kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext) -> Unit,
        opprettOppgave: (oppgaveConfig: OppgaveConfig) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, OppgaveId>,
    ): UtløptFristForKontrollsamtaleContext {
        return Either.catch {
            hentKontrollnotatMottatt(
                sak.saksnummer,
                kontrollsamtale.forventetMottattKontrollnotatIPeriode(),
            ).fold(
                {
                    throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil)
                },
                { erKontrollnotatMottatt ->
                    sessionFactory.withTransactionContext { tx ->
                        when (erKontrollnotatMottatt) {
                            is ErKontrollNotatMottatt.Ja -> {
                                håndterMøttTilKontrollsamtale(
                                    kontrollsamtale = kontrollsamtale,
                                    erKontrollnotatMottatt = erKontrollnotatMottatt,
                                    lagreKontrollsamtale = lagreKontrollsamtale,
                                    tx = tx,
                                    clock = clock,
                                    lagreContext = lagreContext,
                                )
                            }

                            is ErKontrollNotatMottatt.Nei -> {
                                håndterIkkeMøttTilKontrollsamtale(
                                    kontrollsamtale = kontrollsamtale,
                                    lagreKontrollsamtale = lagreKontrollsamtale,
                                    tx = tx,
                                    opprettStans = opprettStans,
                                    iverksettStans = iverksettStans,
                                    clock = clock,
                                    lagreContext = lagreContext,
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
                            kontrollsamtale = kontrollsamtale,
                            error = error,
                            clock = clock,
                            sakInfo = sak.info(),
                            opprettOppgave = opprettOppgave,
                            lagreContext = lagreContext,
                            tx = tx,
                        )
                    }
                }.fold(
                    {
                        logger.error(
                            "Feil: ${it.message} ved håndtering av feilet kontrollsamtale: ${kontrollsamtale.id}",
                            it,
                        )
                        this
                    },
                    {
                        logger.info(
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
        kontrollsamtale: Kontrollsamtale,
        lagreKontrollsamtale: (kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext) -> Unit,
        tx: TransactionContext,
        opprettStans: (sakId: UUID, fraOgMed: LocalDate, transactionContext: TransactionContext) -> OpprettStansTransactionCallback,
        iverksettStans: (id: RevurderingId, transactionContext: TransactionContext) -> IverksettStansTransactionCallback,
        clock: Clock,
        lagreContext: (context: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext) -> Unit,
    ): UtløptFristForKontrollsamtaleContext {
        return kontrollsamtale.settIkkeMøttInnenFrist()
            .fold(
                {
                    throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                },
                { ikkeMøttKontrollsamtale ->
                    lagreKontrollsamtale(
                        ikkeMøttKontrollsamtale,
                        tx,
                    )
                    opprettStans(
                        ikkeMøttKontrollsamtale.sakId,
                        ikkeMøttKontrollsamtale.frist.førsteINesteMåned(),
                        tx,
                    ).let { opprettCallback ->
                        iverksettStans(
                            opprettCallback.revurderingId,
                            tx,
                        ).let { iverksettCallback ->
                            ikkeMøtt(
                                ikkeMøttKontrollsamtale.id,
                                clock,
                            ).let { ctx ->
                                lagreContext(
                                    ctx,
                                    tx,
                                )
                                iverksettCallback.sendUtbetalingCallback()
                                    .getOrElse {
                                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                                    }

                                opprettCallback.sendStatistikkCallback(tx)
                                iverksettCallback.sendStatistikkCallback(tx)
                                ctx
                            }
                        }
                    }
                },
            )
    }

    private fun håndterMøttTilKontrollsamtale(
        kontrollsamtale: Kontrollsamtale,
        erKontrollnotatMottatt: ErKontrollNotatMottatt.Ja,
        lagreKontrollsamtale: (kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext) -> Unit,
        tx: TransactionContext,
        clock: Clock,
        lagreContext: (context: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext) -> Unit,
    ): UtløptFristForKontrollsamtaleContext {
        return kontrollsamtale.settGjennomført(erKontrollnotatMottatt.kontrollnotat.journalpostId)
            .fold(
                {
                    throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                },
                { møttTilKontrollsamtale ->
                    lagreKontrollsamtale(
                        møttTilKontrollsamtale,
                        tx,
                    )
                    prosessert(
                        møttTilKontrollsamtale.id,
                        clock,
                    ).also {
                        lagreContext(
                            it,
                            tx,
                        )
                    }
                },
            )
    }

    private fun håndterFeil(
        kontrollsamtale: Kontrollsamtale,
        error: Throwable,
        clock: Clock,
        sakInfo: SakInfo,
        opprettOppgave: (oppgaveConfig: OppgaveConfig) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, OppgaveId>,
        lagreContext: (context: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext) -> Unit,
        tx: TransactionContext,
    ): UtløptFristForKontrollsamtaleContext {
        return feilet(
            kontrollsamtale.id,
            error.message!!,
            clock,
        ).let { ctx ->
            if (retryLimitReached(kontrollsamtale.id)) {
                val oppgaveId = opprettOppgave(
                    OppgaveConfig.KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale(
                        saksnummer = sakInfo.saksnummer,
                        periode = kontrollsamtale.forventetMottattKontrollnotatIPeriode(),
                        fnr = sakInfo.fnr,
                        clock = clock,
                        sakstype = sakInfo.type,
                    ),
                ).getOrElse { throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil) }

                prosessertMedFeil(
                    kontrollsamtale.id,
                    clock,
                    oppgaveId,
                ).also {
                    logger.info("Maks antall forsøk (${MAX_RETRIES + 1}) for kontrollsamtale:${kontrollsamtale.id} nådd. Gir opp videre prosessering. OppgaveId: $oppgaveId opprettet.", RuntimeException("Genererer stacktrace for enklere debugging."))
                    lagreContext(
                        it,
                        tx,
                    )
                }
            } else {
                ctx.also {
                    lagreContext(
                        ctx,
                        tx,
                    )
                }
            }
        }
    }

    private fun Kontrollsamtale.forventetMottattKontrollnotatIPeriode(): DatoIntervall {
        return DatoIntervall(this.innkallingsdato, this.frist)
    }

    data class KunneIkkeHåndtereUtløptKontrollsamtale(val feil: String)

    private data class FeilVedProsesseringAvKontrollsamtaleException(val msg: String) : RuntimeException(msg)

    /*
    TODO:
        Disse to callbackene burde bare vært kalt in place
     */
    data class OpprettStansTransactionCallback(
        val revurderingId: RevurderingId,
        val sendStatistikkCallback: (tx: SessionContext) -> Unit,
    )

    data class IverksettStansTransactionCallback(
        val sendUtbetalingCallback: () -> Either<Any, Utbetalingsrequest>,
        val sendStatistikkCallback: (tx: SessionContext) -> Unit,
    )

    companion object {
        const val MAX_RETRIES = 2

        fun genererId(fristUtløpDato: LocalDate): NameAndLocalDateId {
            return NameAndLocalDateId(
                name = "HåndterUtløptFristForKontrollsamtale",
                date = fristUtløpDato,
            )
        }
    }
}
