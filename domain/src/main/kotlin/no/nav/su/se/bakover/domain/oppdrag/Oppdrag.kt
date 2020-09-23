package no.nav.su.se.bakover.domain.oppdrag

import java.time.Instant
import java.time.LocalDate
import java.util.*
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver

data class Oppdrag(
    val id: UUID30,
    val opprettet: Instant,
    val sakId: UUID,
    private val utbetalinger: MutableList<Utbetaling> = mutableListOf()
) : PersistentDomainObject<OppdragPersistenceObserver>() {

    val fnr: Fnr by lazy { persistenceObserver.hentFnr(sakId) }

    // TODO jah: Ved samtidige ikke utbetalte behandlinger vil dette bli et problem.
    fun sisteOversendteUtbetaling() = utbetalinger.toList()
        .sortedWith(Utbetaling.Opprettet)
        // Vi ønsker ikke å filtrere ut de som ikke har kvittering, men vi ønsker å filtrere ut de kvitteringene som har feil i seg.
        .filter { !it.erKvittertFeil() }
        .lastOrNull { it.erOversendtOppdrag() }

    fun hentUtbetalinger(): List<Utbetaling> = utbetalinger.toList()

    fun harOversendteUtbetalingerEtter(value: LocalDate) = hentUtbetalinger()
        .filter { !it.erKvittertFeil() }
        .filter { it.erOversendtOppdrag() }
        .flatMap { it.utbetalingslinjer }
        .any {
            it.tom.isEqual(value) || it.tom.isAfter(value)
        }

    fun genererUtbetaling(beregning: Beregning): Utbetaling {
        val utbetalingsperioder = beregning.månedsberegninger
            .groupBy { it.beløp }
            .map {
                Utbetalingsperiode(
                    fom = it.value.minByOrNull { it.fom }!!.fom,
                    tom = it.value.maxByOrNull { it.tom }!!.tom,
                    beløp = it.key,
                )
            }
        return genererUtbetaling(utbetalingsperioder)
    }

    fun genererUtbetaling(utbetalingsperioder: List<Utbetalingsperiode>): Utbetaling {
        return Utbetaling(
            utbetalingslinjer = utbetalingsperioder.map {
                Utbetalingslinje(
                    fom = it.fom,
                    tom = it.tom,
                    forrigeUtbetalingslinjeId = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.id,
                    beløp = it.beløp
                )
            }.also {
                it.zipWithNext { a, b -> b.link(a) }
            },
            fnr = fnr
        )
    }

    fun opprettUtbetaling(utbetaling: Utbetaling): Utbetaling {
        return persistenceObserver.opprettUtbetaling(id, utbetaling)
            .also {
                utbetalinger.add(it)
            }
    }

    fun slettUtbetaling(utbetaling: Utbetaling) = persistenceObserver.slettUtbetaling(utbetaling)

    interface OppdragPersistenceObserver : PersistenceObserver {
        fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling
        fun slettUtbetaling(utbetaling: Utbetaling)
        fun hentFnr(sakId: UUID): Fnr
    }
}
