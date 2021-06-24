package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingJson
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.KanStansesEllerGjenopptas.Companion.kanStansesEllerGjenopptas
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakJson
import no.nav.su.se.bakover.web.routes.vedtak.toJson
import java.time.Clock

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

        companion object {
            internal fun List<Utbetaling>.kanStansesEllerGjenopptas(): KanStansesEllerGjenopptas {
                // TODO jah: Dette er en ad-hoc algoritme, kun for å få noe front-end. Bør bruke det samme som stans/gjenoppta endepunktene.
                val oversendteUtbetalinger = this.hentOversendteUtbetalingerUtenFeil()
                return when {
                    oversendteUtbetalinger.isEmpty() -> INGEN
                    oversendteUtbetalinger.last().type == Utbetaling.UtbetalingsType.STANS -> GJENOPPTA
                    else -> STANS
                }
            }
        }
    }

    private data class UtbetalingslinjeMedUtbetalingstype(
        val utbetalingslinje: Utbetalingslinje,
        val type: Utbetaling.UtbetalingsType,
    ) : KanPlasseresPåTidslinje<UtbetalingslinjeMedUtbetalingstype> {
        override val periode = utbetalingslinje.periode

        override fun copy(args: CopyArgs.Tidslinje) = this.copy(utbetalingslinje = utbetalingslinje.copy(args))

        override val opprettet: Tidspunkt
            get() = utbetalingslinje.opprettet
    }

    companion object {
        internal fun Sak.toJson() = SakJson(
            id = id.toString(),
            saksnummer = saksnummer.nummer,
            fnr = fnr.toString(),
            søknader = søknader.map { it.toJson() },
            behandlinger = behandlinger.map { it.toJson() },
            utbetalinger = utbetalinger.hentOversendteUtbetalingerUtenFeil()
                .flatMap { utbetaling ->
                    utbetaling.utbetalingslinjer.map {
                        it.medUtbetalingstype(utbetaling.type)
                    }
                }.let { utbetalingslinjer ->
                    if (utbetalingslinjer.isEmpty())
                        emptyList()
                    else
                        tidslinjeMedUtbetalinger(utbetalingslinjer)
                },
            utbetalingerKanStansesEllerGjenopptas = utbetalinger.kanStansesEllerGjenopptas(),
            revurderinger = revurderinger.map { it.toJson() },
            vedtak = vedtakListe.map { it.toJson() },
        )

        private fun Utbetalingslinje.medUtbetalingstype(type: Utbetaling.UtbetalingsType) =
            UtbetalingslinjeMedUtbetalingstype(
                utbetalingslinje = when (this) {
                    is Utbetalingslinje.Endring -> hånderEndringer(this)
                    is Utbetalingslinje.Ny -> this
                },
                type,
            )

        private fun hånderEndringer(utbetalingslinje: Utbetalingslinje.Endring) =
            when (utbetalingslinje) {
                is Utbetalingslinje.Endring.Opphør -> {
                    utbetalingslinje.copy(
                        fraOgMed = utbetalingslinje.virkningstidspunkt,
                        beløp = 0,
                    )
                }
                is Utbetalingslinje.Endring.Reaktivering -> {
                    utbetalingslinje.copy(
                        fraOgMed = utbetalingslinje.virkningstidspunkt,
                        beløp = utbetalingslinje.beløp,
                    )
                }
                is Utbetalingslinje.Endring.Stans -> {
                    utbetalingslinje.copy(
                        fraOgMed = utbetalingslinje.virkningstidspunkt,
                        beløp = 0,
                    )
                }
            }

        private fun tidslinjeMedUtbetalinger(utbetalingslinjer: List<UtbetalingslinjeMedUtbetalingstype>): List<UtbetalingJson> =
            Tidslinje(
                periode = Periode.create(
                    fraOgMed = utbetalingslinjer.minOf { it.utbetalingslinje.fraOgMed },
                    tilOgMed = utbetalingslinjer.maxOf { it.utbetalingslinje.tilOgMed },
                ),
                objekter = utbetalingslinjer,
                clock = Clock.systemUTC(),
            ).tidslinje.map {
                UtbetalingJson(
                    id = it.utbetalingslinje.id.toString(),
                    fraOgMed = it.utbetalingslinje.fraOgMed,
                    tilOgMed = it.utbetalingslinje.tilOgMed,
                    beløp = it.utbetalingslinje.beløp,
                    type = it.type.toString(),
                )
            }
    }
}
