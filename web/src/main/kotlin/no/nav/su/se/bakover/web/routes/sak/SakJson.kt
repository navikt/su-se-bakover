package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingJson
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakJson
import no.nav.su.se.bakover.web.routes.vedtak.toJson

internal data class SakJson(
    val id: String,
    val saksnummer: Long,
    val fnr: String,
    val søknader: List<SøknadJson>,
    val behandlinger: List<BehandlingJson>,
    val utbetalinger: List<UtbetalingJson>,
    val utbetalingerKanStansesEllerGjenopptas: KanStansesEllerGjenopptas,
    val revurderinger: List<RevurderingJson>,
    val vedtak: List<VedtakJson>,
) {
    enum class KanStansesEllerGjenopptas {
        STANS,
        GJENOPPTA,
        INGEN;
    }

    companion object {
        internal fun Sak.toJson() = SakJson(
            id = id.toString(),
            saksnummer = saksnummer.nummer,
            fnr = fnr.toString(),
            søknader = søknader.map { it.toJson() },
            behandlinger = behandlinger.map { it.toJson() },
            utbetalinger = utbetalingstidslinje().tidslinje.map {
                UtbetalingJson(
                    fraOgMed = it.periode.fraOgMed,
                    tilOgMed = it.periode.tilOgMed,
                    beløp = it.beløp,
                    type = when (it) {
                        is UtbetalingslinjePåTidslinje.Ny -> Utbetaling.UtbetalingsType.NY
                        is UtbetalingslinjePåTidslinje.Opphør -> Utbetaling.UtbetalingsType.OPPHØR
                        is UtbetalingslinjePåTidslinje.Reaktivering -> Utbetaling.UtbetalingsType.GJENOPPTA
                        is UtbetalingslinjePåTidslinje.Stans -> Utbetaling.UtbetalingsType.STANS
                    }.toString(),
                )
            },
            utbetalingerKanStansesEllerGjenopptas = utbetalingstidslinje().tidslinje.let {
                when {
                    it.isEmpty() -> KanStansesEllerGjenopptas.INGEN
                    it.last() is UtbetalingslinjePåTidslinje.Stans -> KanStansesEllerGjenopptas.GJENOPPTA
                    else -> KanStansesEllerGjenopptas.STANS
                }
            },
            revurderinger = revurderinger.map { it.toJson() },
            vedtak = vedtakListe.map { it.toJson() },
        )
    }
}
