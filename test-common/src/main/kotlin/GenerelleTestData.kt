package no.nav.su.se.bakover.test

import io.kotest.assertions.fail
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Ident
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import kotlin.concurrent.getOrSet

/** Fixed UTC Clock at 2021-01-01T01:02:03.456789000Z */
val fixedClock: Clock = Clock.fixed(1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
fun tikkendeFixedClock() = TikkendeKlokke(fixedClock)

fun fixedClockAt(date: LocalDate = 1.januar(2021)): Clock =
    Clock.fixed(date.atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

/** Fixed UTC clock at 2021-02-08T01:02:03.456789000Z */
val enUkeEtterFixedClock: Clock = fixedClock.plus(7, ChronoUnit.DAYS)

/**
 * Tilsvarer 2021-07-01T01:02:03.456789000Z
 */
val nåtidForSimuleringStub = fixedClock.plus(181, ChronoUnit.DAYS)

/** Fixed UTC Clock */
fun Clock.plus(amountToAdd: Long, unit: TemporalUnit): Clock =
    Clock.fixed(this.instant().plus(amountToAdd, unit), ZoneOffset.UTC)

/**
 * Fixed Tidspunkt at 2021-01-01T01:02:03.456789000Z
 * Correlates with `fixedClock`
 */
val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)

/**
 * Fixed Tidspunkt at 2021-02-08T01:02:03.456789000Z
 * Correlates with [enUkeEtterFixedClock]
 */
val enUkeEtterFixedTidspunkt: Tidspunkt = Tidspunkt.now(enUkeEtterFixedClock)

/**
 * Fixed LocalDate at 2021-01-01
 * Correlates with `fixedClock`
 */
val fixedLocalDate: LocalDate = 1.januar(2021)

val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
val veileder = NavIdentBruker.Veileder("veileder")

const val saksbehandlerNavn = "Sak S. Behandler"

val saksnummer = Saksnummer(nummer = 12345676)

val fnr = Fnr.generer()
val epsFnr = Fnr.generer()
val fnrUnder67 = Fnr("01017001337")
val fnrOver67 = Fnr("05064535694")

val aktørId = AktørId("aktørId")

val sakinfo = SakInfo(
    sakId = sakId,
    saksnummer = saksnummer,
    fnr = fnr,
    type = Sakstype.UFØRE,
)

fun person(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    aktørId: AktørId = no.nav.su.se.bakover.test.aktørId,
    fødsel: Person.Fødsel? = null,
) = Person(
    ident = Ident(
        fnr = fnr,
        aktørId = aktørId,
    ),
    navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    fødsel = fødsel,
)

val stønadsperiode2021 = Stønadsperiode.create(år(2021))
val stønadsperiode2022 = Stønadsperiode.create(år(2022))

val attestant = NavIdentBruker.Attestant("attestant")

fun attesteringIverksatt(clock: Clock = fixedClock) = Attestering.Iverksatt(
    attestant = attestant,
    opprettet = Tidspunkt.now(clock),
)

fun attesteringUnderkjent(
    clock: Clock = fixedClock,
) = Attestering.Underkjent(
    attestant = attestant,
    grunn = Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER,
    kommentar = "attesteringUnderkjent",
    opprettet = Tidspunkt.now(clock),
)

class TestSessionFactory : SessionFactory {

    companion object {
        // Gjør det enklere å verifisere i testene.
        val sessionContext = object : SessionContext {
            override fun isClosed() = false
        }
        val transactionContext = object : TransactionContext {
            override fun isClosed() = false
        }
    }

    override fun <T> withSessionContext(action: (SessionContext) -> T): T =
        SessionCounter().withCountSessions { action(sessionContext) }

    override fun <T> withTransactionContext(action: (TransactionContext) -> T): T =
        SessionCounter().withCountSessions { action(transactionContext) }

    override fun <T> use(transactionContext: TransactionContext, action: (TransactionContext) -> T): T {
        return SessionCounter().withCountSessions { action(transactionContext) }
    }

    override fun newSessionContext() = sessionContext
    override fun newTransactionContext() = transactionContext

    // TODO jah: Denne er duplikat med den som ligger i database siden test-common ikke har en referanse til database-modulen.
    private class SessionCounter {
        private val activeSessionsPerThread: ThreadLocal<Int> = ThreadLocal()

        fun <T> withCountSessions(action: () -> T): T {
            return activeSessionsPerThread.getOrSet { 0 }.inc().let {
                if (it > 1) {
                    fail("Database sessions were over the threshold while running test.")
                }
                activeSessionsPerThread.set(it)
                try {
                    action()
                } finally {
                    activeSessionsPerThread.set(activeSessionsPerThread.getOrSet { 1 }.dec())
                }
            }
        }
    }
}
