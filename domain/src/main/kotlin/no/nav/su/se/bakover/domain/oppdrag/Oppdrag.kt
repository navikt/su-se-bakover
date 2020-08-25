package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver
import java.time.Instant
import java.util.UUID

data class Oppdrag(
    val id: UUID30 = UUID30.randomUUID(),
    val opprettet: Instant = now(),
    val sakId: UUID,
    private val utbetalinger: MutableList<Utbetaling> = mutableListOf()
) : PersistentDomainObject<OppdragPersistenceObserver>() {
    fun sisteUtbetaling() = utbetalinger.lastOrNull() // TODO Må implementere konsept om utbetalt og sjekke om det finnes en utbetaling

    fun harUtbetalinger() = utbetalinger.isNotEmpty() // TODO Må implementere konsept om utbetalt og sjekke om det finnes en utbetaling

    fun hentUtbetalinger(): List<Utbetaling> = utbetalinger.toList()

    fun generererUtbetaling(behandlingId: UUID, beregningsperioder: List<BeregningsPeriode>): Utbetaling {
        return Utbetaling(
            oppdragId = id,
            behandlingId = behandlingId,
            utbetalingslinjer = beregningsperioder.map {
                Utbetalingslinje(
                    fom = it.fom,
                    tom = it.tom,
                    forrigeUtbetalingslinjeId = if (harUtbetalinger()) sisteUtbetaling()!!.sisteOppdragslinje().id else null,
                    beløp = it.beløp
                )
            }.also {
                it.zipWithNext { a, b -> b.link(a) }
            }
        )
    }

    fun opprettUtbetaling(utbetaling: Utbetaling) = persistenceObserver.opprettUbetaling(id, utbetaling).also {
        utbetalinger.add(it)
    }

    interface OppdragPersistenceObserver : PersistenceObserver {
        fun opprettUbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling
    }
}
