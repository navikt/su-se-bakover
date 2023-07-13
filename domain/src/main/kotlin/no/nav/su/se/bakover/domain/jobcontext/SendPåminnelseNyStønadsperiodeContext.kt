package no.nav.su.se.bakover.domain.jobcontext

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.avrund
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * Holder oversikt over tilstanden for en instans av denne jobben, definert av [id].
 * Dersom jobben kjøres flere ganger for samme [id], vil context gjenbrukes og oppdateres med nye verdier.
 *
 * @param id identifikator utledet av navneet på jobben, år og måned
 * @param prosessert liste over alle saksnummer som har blitt prosessert av denne jobb-instansen
 * @param sendt liste over alle saksnummer hvor det er sendt ut påminnelse om ny stønadsperiode av denne jobb-instansen.
 * @param feilede liste over alle saksnummer med tilhørende feil
 *  Saker som er [sendt] er også [prosessert], men ikke nødvendigvis omvendt.
 */
data class SendPåminnelseNyStønadsperiodeContext(
    private val id: NameAndYearMonthId,
    private val opprettet: Tidspunkt,
    private val endret: Tidspunkt,
    private val prosessert: Set<Saksnummer>,
    private val sendt: Set<Saksnummer>,
    val feilede: List<Feilet>,
) : JobContext {

    data class Feilet(val saksnummer: Saksnummer, val feil: String)

    constructor(
        clock: Clock,
        id: NameAndYearMonthId = genererIdForTidspunkt(clock),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        endret: Tidspunkt = opprettet,
        prosessert: Set<Saksnummer> = emptySet(),
        sendt: Set<Saksnummer> = emptySet(),
        feilet: List<Feilet> = emptyList(),
    ) : this(
        id,
        opprettet,
        endret,
        prosessert,
        sendt,
        feilet,
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

    fun feilet(feilet: Feilet, clock: Clock): SendPåminnelseNyStønadsperiodeContext =
        copy(feilede = this.feilede + feilet, endret = Tidspunkt.now(clock))

    fun oppsummering(clock: Clock): String {
        return """
            ${"\n"}
            ***********************************
            Oppsummering av jobb: ${id.name}, tidspunkt:${Tidspunkt.now(clock)},
            Måned: ${id.yearMonth},
            Opprettet: $opprettet,
            Endret: $endret,
            Prosessert: $prosessert,
            Sendt: $sendt,
            Feilet: ${feilede.map { it.saksnummer }}
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
                        lagreDokument(dokument.leggTilMetadata(metadata = Dokument.Metadata(sakId = sak.id)), tx)
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
                name = "SendPåminnelseNyStønadsperiode",
                yearMonth = YearMonth.now(clock),
            )
        }
    }

    sealed interface KunneIkkeSendePåminnelse {
        data object FantIkkePerson : KunneIkkeSendePåminnelse
        data object KunneIkkeLageBrev : KunneIkkeSendePåminnelse
    }
}
