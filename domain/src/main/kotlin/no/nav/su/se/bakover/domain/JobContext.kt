package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.avrund
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

sealed class JobContext {

    abstract fun id(): JobContextId

    enum class Typer {
        SendPåminnelseNyStønadsperiode
    }
}

/**
 * Holder oversikt over tilstanden for en instans av denne jobben, definert av [id].
 * Dersom jobben kjøres flere ganger for samme [id], vil context gjenbrukes og oppdateres med nye verdier.
 *
 * @param id identifikator utledet av navneet på jobben, år og måned
 * @param prosessert liste over alle saksnummer som har blitt prosessert av denne jobb-instansen
 * @param sendt liste over alle saksnummer hvor det er sendt ut påminnelse om ny stønadsperiode av denne jobb-instansen.
 *  Saker som er [sendt] er også [prosessert], men ikke nødvendigvis omvendt.
 */
data class SendPåminnelseNyStønadsperiodeContext(
    private val id: NameAndYearMonthId,
    private val opprettet: Tidspunkt,
    private val endret: Tidspunkt,
    private val prosessert: Set<Saksnummer>,
    private val sendt: Set<Saksnummer>,
) : JobContext() {

    constructor(
        clock: Clock,
        id: NameAndYearMonthId = genererIdForTidspunkt(clock),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        endret: Tidspunkt = opprettet,
        prosessert: Set<Saksnummer> = emptySet(),
        sendt: Set<Saksnummer> = emptySet(),
    ) : this(
        id, opprettet, endret, prosessert, sendt
    )

    override fun id(): NameAndYearMonthId {
        return id
    }

    fun opprettet(): Tidspunkt {
        return opprettet
    }

    fun endret(): Tidspunkt {
        return endret
    }

    private fun prosessert(saksnummer: Saksnummer, clock: Clock): SendPåminnelseNyStønadsperiodeContext {
        return copy(prosessert = prosessert + saksnummer, endret = Tidspunkt.now(clock))
    }

    fun prosessert(): Set<Saksnummer> {
        return prosessert
    }

    fun sendt(): Set<Saksnummer> {
        return sendt
    }

    private fun sendt(saksnummer: Saksnummer, clock: Clock): SendPåminnelseNyStønadsperiodeContext {
        return prosessert(saksnummer, clock).copy(sendt = sendt + saksnummer, endret = Tidspunkt.now(clock))
    }

    fun oppsummering(clock: Clock): String {
        return """
            ${"\n"}
            ***********************************
            Oppsummering av jobb: ${id.jobName}, tidspunkt:${Tidspunkt.now(clock)},
            Måned: ${id.yearMonth},
            Opprettet: $opprettet,
            Endret: $endret,
            Prosessert: $prosessert,
            Sendt: $sendt
            ***********************************
            ${"\n"}
        """.trimIndent()
    }

    fun uprosesserte(alle: () -> List<SakInfo>): Set<Saksnummer> {
        return alle().map { it.saksnummer }.toSet().minus(prosessert())
    }

    private fun skalSendePåminnelse(sak: Sak): Boolean {
        return sak.ytelseUtløperVedUtløpAv(id().tilPeriode())
    }

    fun håndter(
        sak: Sak,
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeSendePåminnelse.FantIkkePerson, Person>,
        sessionFactory: SessionFactory,
        lagDokument: (request: LagBrevRequest.PåminnelseNyStønadsperiode) -> Either<KunneIkkeSendePåminnelse.KunneIkkeLageBrev, Dokument.UtenMetadata>,
        lagreDokument: (dokument: Dokument.MedMetadata, tx: TransactionContext) -> Unit,
        lagreContext: (context: SendPåminnelseNyStønadsperiodeContext, tx: TransactionContext) -> Unit,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeSendePåminnelse, SendPåminnelseNyStønadsperiodeContext> {
        return if (skalSendePåminnelse(sak)) {
            hentPerson(sak.fnr)
                .flatMap { person ->
                    val dagensDato = LocalDate.now(clock)
                    lagDokument(
                        LagBrevRequest.PåminnelseNyStønadsperiode(
                            person = person,
                            dagensDato = dagensDato,
                            saksnummer = sak.saksnummer,
                            utløpsdato = id().yearMonth.atEndOfMonth(),
                            halvtGrunnbeløp = formuegrenserFactory.forDato(dagensDato).formuegrense.avrund(),
                        ),
                    )
                }.map { dokument ->
                    sessionFactory.withTransactionContext { tx ->
                        lagreDokument(
                            dokument.leggTilMetadata(
                                metadata = Dokument.Metadata(
                                    sakId = sak.id,
                                    bestillBrev = true,
                                ),
                            ),
                            tx,
                        )
                        sendt(sak.saksnummer, clock).also {
                            lagreContext(it, tx)
                        }
                    }
                }
        } else {
            sessionFactory.withTransactionContext { tx ->
                prosessert(sak.saksnummer, clock).also {
                    lagreContext(it, tx)
                }
            }.right()
        }
    }

    companion object {
        fun genererIdForTidspunkt(clock: Clock): NameAndYearMonthId {
            return NameAndYearMonthId(
                jobName = type().toString(),
                yearMonth = YearMonth.now(clock),
            )
        }

        fun type(): Typer {
            return Typer.SendPåminnelseNyStønadsperiode
        }
    }

    sealed interface KunneIkkeSendePåminnelse {
        object FantIkkePerson : KunneIkkeSendePåminnelse
        object KunneIkkeLageBrev : KunneIkkeSendePåminnelse
    }
}

interface JobContextId {
    fun value(): String
}

data class NameAndYearMonthId(
    val jobName: String,
    val yearMonth: YearMonth,
) : JobContextId {
    override fun value(): String {
        return """$jobName$yearMonth"""
    }

    fun tilPeriode(): Periode {
        return Periode.create(
            fraOgMed = LocalDate.of(yearMonth.year, yearMonth.month, 1),
            tilOgMed = yearMonth.atEndOfMonth(),
        )
    }
}

interface JobContextRepo {
    fun <T : JobContext> hent(id: JobContextId): T?
    fun lagre(jobContext: JobContext, transactionContext: TransactionContext)
}
