package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import java.math.BigDecimal

sealed interface Tilbakekrevingsvedtak {
    val vedtakId: String
    val ansvarligEnhet: String
    val kontrollFelt: String
    val behandler: NavIdentBruker
    val tilbakekrevingsperioder: List<Tilbakekrevingsperiode>

    data class FullTilbakekreving(
        override val vedtakId: String,
        override val ansvarligEnhet: String,
        override val kontrollFelt: String,
        override val behandler: NavIdentBruker,
        override val tilbakekrevingsperioder: List<Tilbakekrevingsperiode>,
    ) : Tilbakekrevingsvedtak

    data class IngenTilbakekreving(
        override val vedtakId: String,
        override val ansvarligEnhet: String,
        override val kontrollFelt: String,
        override val behandler: NavIdentBruker,
        override val tilbakekrevingsperioder: List<Tilbakekrevingsperiode>,
    ) : Tilbakekrevingsvedtak

    data class Tilbakekrevingsperiode(
        val periode: Periode,
        val renterBeregnes: Boolean,
        val beløpRenter: BigDecimal,
        val tilbakekrevingsbeløp: List<Tilbakekrevingsbeløp>,
    ) {
        sealed interface Tilbakekrevingsbeløp {
            val kodeKlasse: KlasseKode
            val beløpTidligereUtbetaling: BigDecimal
            val beløpNyUtbetaling: BigDecimal
            val beløpSomSkalTilbakekreves: BigDecimal
            val beløpSomIkkeTilbakekreves: BigDecimal

            data class TilbakekrevingsbeløpFeilutbetaling(
                override val kodeKlasse: KlasseKode,
                override val beløpTidligereUtbetaling: BigDecimal,
                override val beløpNyUtbetaling: BigDecimal,
                override val beløpSomSkalTilbakekreves: BigDecimal,
                override val beløpSomIkkeTilbakekreves: BigDecimal,
            ) : Tilbakekrevingsbeløp {
                init {
                    require(kodeKlasse == KlasseKode.KL_KODE_FEIL_INNT)
                }
            }

            data class TilbakekrevingsbeløpYtelse(
                override val kodeKlasse: KlasseKode,
                override val beløpTidligereUtbetaling: BigDecimal,
                override val beløpNyUtbetaling: BigDecimal,
                override val beløpSomSkalTilbakekreves: BigDecimal,
                override val beløpSomIkkeTilbakekreves: BigDecimal,
                val beløpSkatt: BigDecimal,
                val tilbakekrevingsresultat: Tilbakekrevingsresultat,
                val skyld: Skyld,
            ) : Tilbakekrevingsbeløp {
                init {
                    require(kodeKlasse == KlasseKode.SUUFORE)
                }
            }
        }
    }

    enum class Tilbakekrevingsresultat {
        FULL_TILBAKEKREVING,
        INGEN_TILBAKEKREVING
    }

    enum class Skyld {
        BRUKER,
        IKKE_FORDELT,
    }
}
