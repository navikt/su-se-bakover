package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.tilbakekreving.domain.KlasseKode
import java.math.BigDecimal

sealed interface Tilbakekrevingsvedtak {
    val vedtakId: String
    val ansvarligEnhet: String
    val kontrollFelt: String
    val behandler: NavIdentBruker
    val tilbakekrevingsperioder: List<Tilbakekrevingsperiode>

    fun netto(): Månedsbeløp {
        return Månedsbeløp(månedbeløp = tilbakekrevingsperioder.map { it.netto() })
    }

    fun brutto(): Månedsbeløp {
        return Månedsbeløp(månedbeløp = tilbakekrevingsperioder.map { it.brutto() })
    }

    fun skatt(): Månedsbeløp {
        return Månedsbeløp(månedbeløp = tilbakekrevingsperioder.map { it.skatt() })
    }

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
        fun brutto(): MånedBeløp {
            return MånedBeløp(
                periode.tilMåned(),
                tilbakekrevingsbeløp.filterIsInstance<Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse>()
                    .fold(Beløp.zero()) { acc, tilbakekrevingsbeløpYtelse -> acc + tilbakekrevingsbeløpYtelse.brutto() },
            )
        }

        fun netto(): MånedBeløp {
            return MånedBeløp(
                periode.tilMåned(),
                tilbakekrevingsbeløp.filterIsInstance<Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse>()
                    .fold(Beløp.zero()) { acc, tilbakekrevingsbeløpYtelse -> acc + tilbakekrevingsbeløpYtelse.netto() },
            )
        }

        fun skatt(): MånedBeløp {
            return MånedBeløp(
                periode.tilMåned(),
                tilbakekrevingsbeløp.filterIsInstance<Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse>()
                    .fold(Beløp.zero()) { acc, tilbakekrevingsbeløpYtelse -> acc + tilbakekrevingsbeløpYtelse.skatt() },
            )
        }

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

                fun brutto(): Beløp {
                    return Beløp(beløpSomSkalTilbakekreves.intValueExact())
                }

                fun netto(): Beløp {
                    return Beløp(beløpSomSkalTilbakekreves.intValueExact() - beløpSkatt.intValueExact())
                }

                fun skatt(): Beløp {
                    return Beløp(beløpSkatt.intValueExact())
                }
            }
        }
    }

    enum class Tilbakekrevingsresultat {
        FULL_TILBAKEKREVING,
        INGEN_TILBAKEKREVING,
    }

    enum class Skyld {
        BRUKER,
        IKKE_FORDELT,
    }
}
