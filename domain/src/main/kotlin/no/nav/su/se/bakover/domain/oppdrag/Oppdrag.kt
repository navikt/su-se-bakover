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
    fun sisteUtbetaling() = utbetalinger.toList()
        .sortedWith(Utbetaling.Opprettet)
        .lastOrNull() { it.erUtbetalt() }

    fun hentUtbetalinger(): List<Utbetaling> = utbetalinger.toList()

    fun generererUtbetaling(beregningsperioder: List<BeregningsPeriode>): Utbetaling {
        return Utbetaling(
            utbetalingslinjer = beregningsperioder.map {
                Utbetalingslinje(
                    fom = it.fom,
                    tom = it.tom,
                    forrigeUtbetalingslinjeId = sisteUtbetaling()?.sisteUtbetalingslinje()?.id,
                    beløp = it.beløp
                )
            }.also {
                it.zipWithNext { a, b -> b.link(a) }
            }
        )
    }

    fun opprettUtbetaling(utbetaling: Utbetaling, behandlingId: UUID): Utbetaling {
        return persistenceObserver.opprettUtbetaling(id, utbetaling, behandlingId)
            .also {
                utbetalinger.add(it)
            }
    }

    fun slettUtbetaling(utbetaling: Utbetaling) = persistenceObserver.slettUtbetaling(utbetaling)

    interface OppdragPersistenceObserver : PersistenceObserver {
        fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling, behandlingId: UUID): Utbetaling
        fun slettUtbetaling(utbetaling: Utbetaling)
    }
}
