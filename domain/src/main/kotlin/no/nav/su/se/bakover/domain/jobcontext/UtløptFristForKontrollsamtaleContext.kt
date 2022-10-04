package no.nav.su.se.bakover.domain.jobcontext

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.sak.SakInfo
import org.slf4j.LoggerFactory
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
) : JobContext() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    constructor(
        clock: Clock,
        id: NameAndLocalDateId = genererIdForTidspunkt(clock),
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
        return copy(prosessert = prosessert + id, ikkeMøtt = ikkeMøtt + id, feilet = fjernHvisFeilet, endret = Tidspunkt.now(clock))
    }

    fun ikkeMøtt(): Set<UUID> {
        return ikkeMøtt
    }

    fun feilet(id: UUID, feil: String, clock: Clock): UtløptFristForKontrollsamtaleContext {
        return feilet.find { it.id == id }?.let {
            copy(feilet = feilet.minus(it) + it.retried(feil), endret = Tidspunkt.now(clock))
        } ?: copy(feilet = feilet + Feilet(id, 0, feil), endret = Tidspunkt.now(clock))
    }

    fun feilet(): Set<Feilet> {
        return feilet
    }

    data class Feilet(
        val id: UUID,
        val retries: Int,
        val feil: String,
    ) {
        fun retried(feil: String): Feilet {
            return copy(retries = retries + 1, feil = feil)
        }
    }

    fun uprosesserte(utestående: () -> List<UUID>): Set<UUID> {
        return (utestående().toSet()).minus(prosessert())
    }

    fun oppsummering(): String {
        return """
            ${"\n"}
            ***********************************
            Oppsummering av jobb: ${id.jobName}, tidspunkt:${Tidspunkt.now()},
            Dato: ${id.date},
            Opprettet: $opprettet,
            Endret: $endret,
            Prosessert: $prosessert,
            IkkeMøtt: $ikkeMøtt,
            Feilet: $feilet
            ***********************************
            ${"\n"}
        """.trimIndent()
    }

    fun håndter(
        kontrollsamtale: Kontrollsamtale,
        hentSakInfo: (sakId: UUID) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, SakInfo>,
        hentKontrollnotatMottatt: (saksnummer: Saksnummer, periode: Periode) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, ErKontrollNotatMottatt>,
        sessionFactory: SessionFactory,
        opprettStans: (sakId: UUID, fraOgMed: LocalDate, transactionContext: TransactionContext) -> OpprettStansTransactionCallback,
        iverksettStans: (id: UUID, transactionContext: TransactionContext) -> IverksettStansTransactionCallback,
        lagreContext: (context: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext) -> Unit,
        clock: Clock,
        lagreKontrollsamtale: (kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext) -> Unit,
    ): UtløptFristForKontrollsamtaleContext {
        return Either.catch {
            hentSakInfo(kontrollsamtale.sakId)
                .fold(
                    {
                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                    },
                    { sakInfo ->
                        hentKontrollnotatMottatt(
                            sakInfo.saksnummer,
                            Periode.create(
                                fraOgMed = kontrollsamtale.innkallingsdato,
                                tilOgMed = kontrollsamtale.frist,
                            ),
                        ).fold(
                            {
                                throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                            },
                            { erKontrollnotatMottatt ->
                                sessionFactory.withTransactionContext { tx ->
                                    when (erKontrollnotatMottatt) {
                                        is ErKontrollNotatMottatt.Ja -> {
                                            kontrollsamtale.settGjennomført(erKontrollnotatMottatt.kontrollnotat.journalpostId)
                                                .fold(
                                                    {
                                                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                                                    },
                                                    { møttTilKontrollsamtale ->
                                                        lagreKontrollsamtale(møttTilKontrollsamtale, tx)
                                                        prosessert(møttTilKontrollsamtale.id, clock).also {
                                                            lagreContext(it, tx)
                                                        }
                                                    },
                                                )
                                        }
                                        is ErKontrollNotatMottatt.Nei -> {
                                            kontrollsamtale.settIkkeMøttInnenFrist()
                                                .fold(
                                                    {
                                                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                                                    },
                                                    { ikkeMøttKontrollsamtale ->
                                                        lagreKontrollsamtale(ikkeMøttKontrollsamtale, tx)
                                                        // TODO stans fra inneværnde måned hvis mulig
                                                        opprettStans(sakInfo.sakId, ikkeMøttKontrollsamtale.frist.førsteINesteMåned(), tx).let { opprettCallback ->
                                                            iverksettStans(opprettCallback.revurderingId, tx).let { iverksettCallback ->
                                                                ikkeMøtt(ikkeMøttKontrollsamtale.id, clock).let { ctx ->
                                                                    lagreContext(ctx, tx)
                                                                    iverksettCallback.sendUtbetalingCallback()
                                                                        .getOrHandle {
                                                                            throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                                                                        }
                                                                    opprettCallback.sendStatistikkCallback()
                                                                    iverksettCallback.sendStatistikkCallback()
                                                                    ctx
                                                                }
                                                            }
                                                        }
                                                    },
                                                )
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
        }.fold(
            { error ->
                sessionFactory.withTransactionContext { tx ->
                    logger.error("Feil: ${error.message!!} ved prosessering av kontrollsamtale: ${kontrollsamtale.id}")
                    feilet(kontrollsamtale.id, error.message!!, clock).also {
                        lagreContext(it, tx)
                    }
                }
            },
            {
                it
            },
        )
    }

    data class KunneIkkeHåndtereUtløptKontrollsamtale(val feil: String)

    private data class FeilVedProsesseringAvKontrollsamtaleException(val msg: String) : RuntimeException(msg)

    data class OpprettStansTransactionCallback(
        val revurderingId: UUID,
        val sendStatistikkCallback: () -> Unit,
    )
    data class IverksettStansTransactionCallback(
        val sendUtbetalingCallback: () -> Either<Any, Utbetalingsrequest>,
        val sendStatistikkCallback: () -> Unit,
    )

    companion object {
        fun genererIdForTidspunkt(clock: Clock): NameAndLocalDateId {
            return NameAndLocalDateId(
                jobName = type().toString(),
                date = LocalDate.now(clock),
            )
        }

        fun type(): Typer {
            return Typer.KontrollsamtaleFristUtløptContext
        }
    }
}
