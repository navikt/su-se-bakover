package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

/** Fixed UTC Clock at 2021-01-01T01:02:03.456789000Z */
val fixedClock: Clock =
    Clock.fixed(1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
val nåtidForSimuleringStub = fixedClock.plus(200, ChronoUnit.DAYS)

/** Fixed UTC Clock */
fun Clock.plus(amountToAdd: Long, unit: TemporalUnit): Clock =
    Clock.fixed(this.instant().plus(amountToAdd, unit), ZoneOffset.UTC)

/**
 * Fixed Tidspunkt at 2021-01-01T01:02:03.456789000Z
 * Correlates with `fixedClock`
 */
val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)

/**
 * Fixed LocalDate at 2021-01-01
 * Correlates with `fixedClock`
 */
val fixedLocalDate: LocalDate = LocalDate.of(2021, 1, 1)

val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")

const val saksbehandlerNavn = "Sak S. Behandler"

val saksnummer = Saksnummer(nummer = 12345676)

val fnr = Fnr.generer()
val epsFnr = Fnr.generer()

val aktørId = AktørId("aktørId")

fun person(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    aktørId: AktørId = no.nav.su.se.bakover.test.aktørId,
) = Person(
    ident = Ident(
        fnr = fnr,
        aktørId = aktørId,
    ),
    navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
)

val stønadsperiode2021 = Stønadsperiode.create(periode2021, "stønadsperiode2021")

val attestant = NavIdentBruker.Attestant("attestant")
const val attestantNavn = "Att E. Stant"

fun attesteringIverksatt(clock: Clock) = Attestering.Iverksatt(
    attestant = attestant,
    opprettet = Tidspunkt.now(clock),
)

fun attesteringUnderkjent(clock: Clock) = Attestering.Underkjent(
    attestant = attestant,
    grunn = Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER,
    kommentar = "attesteringUnderkjent",
    opprettet = Tidspunkt.now(clock)
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

    override fun <T> withSessionContext(action: (SessionContext) -> T) = action(sessionContext)
    override fun <T> withTransactionContext(action: (TransactionContext) -> T) = action(transactionContext)
}
