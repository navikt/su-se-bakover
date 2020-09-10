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

    fun generererUtbetaling(behandlingId: UUID, beregningsperioder: List<BeregningsPeriode>): Utbetaling {
        return Utbetaling(
            behandlingId = behandlingId,
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

    fun opprettUtbetaling(utbetaling: Utbetaling): Utbetaling {
        check(
            utbetalinger.filter { it.behandlingId == utbetaling.behandlingId }.all { it.kanSlettes() }
        ) { "Behandling ${utbetaling.behandlingId} har en utbetaling som har kommet for langt i behandlingsløpet til å slettes" }
        removeUtbetalingerWith(utbetaling.behandlingId)
        return persistenceObserver.opprettUbetaling(id, utbetaling)
            .also {
                utbetalinger.add(it)
            }
    }

    private fun removeUtbetalingerWith(behandlingId: UUID) = utbetalinger.removeAll {
        it.behandlingId == behandlingId && it.kanSlettes()
    }

    interface OppdragPersistenceObserver : PersistenceObserver {
        fun opprettUbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling
    }
}
