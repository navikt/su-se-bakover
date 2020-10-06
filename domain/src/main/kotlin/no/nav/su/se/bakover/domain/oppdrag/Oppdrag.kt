package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver
import java.time.LocalDate
import java.util.UUID

data class Oppdrag(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    private val utbetalinger: MutableList<Utbetaling> = mutableListOf()
) : PersistentDomainObject<OppdragPersistenceObserver>() {

    val fnr: Fnr by lazy { persistenceObserver.hentFnr(sakId) }

    fun sisteOversendteUtbetaling(): Utbetaling? = oversendteUtbetalinger().lastOrNull()

    /**
     * Returnerer alle utbetalinger som tilhører oppdraget i den rekkefølgen de er opprettet.
     *
     * Uavhengig om de er oversendt/prøvd oversendt/kvitter ok eller kvittert feil.
     */
    fun hentUtbetalinger(): List<Utbetaling> = utbetalinger
        .sortedBy { it.opprettet.instant }

    /**
     * Returnerer utbetalingene sortert økende etter tidspunktet de er sendt til oppdrag. Filtrer bort de som er kvittert feil.
     * TODO jah: Ved initialisering e.l. gjør en faktisk verifikasjon på at ref-verdier på utbetalingslinjene har riktig rekkefølge
     */
    fun oversendteUtbetalinger(): List<Utbetaling> = utbetalinger.filter {
        // Vi ønsker ikke å filtrere bort de som ikke har kvittering, men vi ønsker å filtrere bort de kvitteringene som har feil i seg.
        it.erOversendt() && !it.erKvittertFeil()
    }.sortedBy { it.getOppdragsmelding()!!.avstemmingsnøkkel }

    fun harOversendteUtbetalingerEtter(value: LocalDate) = oversendteUtbetalinger()
        .flatMap { it.utbetalingslinjer }
        .any {
            it.tilOgMed.isEqual(value) || it.tilOgMed.isAfter(value)
        }

    fun genererUtbetaling(beregning: Beregning): Utbetaling {
        val utbetalingsperioder = beregning.månedsberegninger
            .groupBy { it.beløp }
            .map {
                Utbetalingsperiode(
                    fraOgMed = it.value.minByOrNull { it.fraOgMed }!!.fraOgMed,
                    tilOgMed = it.value.maxByOrNull { it.tilOgMed }!!.tilOgMed,
                    beløp = it.key,
                )
            }
        return genererUtbetaling(utbetalingsperioder)
    }

    fun genererUtbetaling(utbetalingsperioder: List<Utbetalingsperiode>): Utbetaling {
        return Utbetaling(
            utbetalingslinjer = utbetalingsperioder.map {
                Utbetalingslinje(
                    fraOgMed = it.fraOgMed,
                    tilOgMed = it.tilOgMed,
                    forrigeUtbetalingslinjeId = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.id,
                    beløp = it.beløp
                )
            }.also {
                it.zipWithNext { a, b -> b.link(a) }
            },
            fnr = fnr
        )
    }

    fun leggTilUtbetaling(utbetaling: Utbetaling) {
        return persistenceObserver.opprettUtbetaling(id, utbetaling)
            .also {
                utbetalinger.add(utbetaling)
            }
    }

    fun slettUtbetaling(utbetaling: Utbetaling) = persistenceObserver.slettUtbetaling(utbetaling)

    interface OppdragPersistenceObserver : PersistenceObserver {
        fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling)
        fun slettUtbetaling(utbetaling: Utbetaling)
        fun hentFnr(sakId: UUID): Fnr
    }
}
