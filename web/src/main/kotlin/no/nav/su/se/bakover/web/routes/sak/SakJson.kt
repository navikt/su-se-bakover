package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingslinjeJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.KanStansesEllerGjenopptas.Companion.kanStansesEllerGjenopptas
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson

internal data class SakJson(
    val id: String,
    val fnr: String,
    val søknader: List<SøknadJson>,
    val behandlinger: List<BehandlingJson>,
    val utbetalinger: List<UtbetalingslinjeJson>,
    val utbetalingerKanStansesEllerGjenopptas: KanStansesEllerGjenopptas,
) {
    enum class KanStansesEllerGjenopptas {
        STANS,
        GJENOPPTA,
        INGEN;

        companion object {
            internal fun Oppdrag.kanStansesEllerGjenopptas(): KanStansesEllerGjenopptas {
                // TODO jah: Dette er en ad-hoc algoritme, kun for å få noe front-end. Bør bruke det samme som stans/gjenoppta endepunktene.
                val oversendteUtbetalinger = this.oversendteUtbetalinger()
                return when {
                    oversendteUtbetalinger.isEmpty() -> INGEN
                    oversendteUtbetalinger.last().type == Utbetaling.UtbetalingType.STANS -> GJENOPPTA
                    else -> STANS
                }
            }
        }
    }

    companion object {
        internal fun Sak.toJson() = SakJson(
            id = id.toString(),
            fnr = fnr.toString(),
            søknader = søknader().map { it.toJson() },
            behandlinger = behandlinger().map { it.toJson() },
            utbetalinger = oppdrag.hentUtbetalinger()
                .filterIsInstance(Utbetaling.OversendtUtbetaling::class.java)
                .flatMap { utbetaling ->
                    utbetaling.utbetalingslinjer.map { utbetalingslinje ->
                        UtbetalingslinjeJson(
                            id = utbetalingslinje.id.toString(),
                            fraOgMed = utbetalingslinje.fraOgMed,
                            tilOgMed = utbetalingslinje.tilOgMed,
                            beløp = utbetalingslinje.beløp,
                            type = utbetaling.type.toString()
                        )
                    }
                },
            utbetalingerKanStansesEllerGjenopptas = oppdrag.kanStansesEllerGjenopptas()
        )
    }
}
