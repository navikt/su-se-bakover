package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import java.math.BigDecimal

sealed interface Tilbakekrevingsvedtak {
    val aksjonsKode: AksjonsKode
    val vedtakId: String
    val hjemmel: TilbakekrevingsHjemmel
    val renterBeregnes: Boolean
    val ansvarligEnhet: String
    val kontrollFelt: String
    val behandler: NavIdentBruker
    val tilbakekrevingsperioder: List<Tilbakekrevingsperiode>

    data class FullTilbakekreving(
        override val aksjonsKode: AksjonsKode,
        override val vedtakId: String,
        override val hjemmel: TilbakekrevingsHjemmel,
        override val renterBeregnes: Boolean,
        override val ansvarligEnhet: String,
        override val kontrollFelt: String,
        override val behandler: NavIdentBruker,
        override val tilbakekrevingsperioder: List<Tilbakekrevingsperiode>,
    ) : Tilbakekrevingsvedtak

    data class IngenTilbakekreving(
        override val aksjonsKode: AksjonsKode,
        override val vedtakId: String,
        override val hjemmel: TilbakekrevingsHjemmel,
        override val renterBeregnes: Boolean,
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
            ) : Tilbakekrevingsbeløp

            data class TilbakekrevingsbeløpYtelse(
                override val kodeKlasse: KlasseKode,
                override val beløpTidligereUtbetaling: BigDecimal,
                override val beløpNyUtbetaling: BigDecimal,
                override val beløpSomSkalTilbakekreves: BigDecimal,
                override val beløpSomIkkeTilbakekreves: BigDecimal,
                val beløpSkatt: BigDecimal,
                val tilbakekrevingsresultat: Tilbakekrevingsresultat,
                val tilbakekrevingsÅrsak: TilbakekrevingsÅrsak,
                val skyld: Skyld,
            ) : Tilbakekrevingsbeløp
        }
    }

    enum class AksjonsKode(val nummer: String) {
        FATT_VEDTAK("8")
    }

    enum class TilbakekrevingsHjemmel {
        ANNEN
    }

    enum class Tilbakekrevingsresultat {
        DELVIS_TILBAKEKREV,
        FEILREGISTRERT,
        FORELDET,
        FULL_TILBAKEKREV,
        INGEN_TILBAKEKREV
    }

    enum class TilbakekrevingsÅrsak {
        ANNET,
        ARBHOYINNT,
        BEREGNFEIL,
        DODSFALL,
        EKTESKAP,
        FEILREGEL,
        FEILUFOREG,
        FLYTTUTLAND,
        IKKESJEKKYTELSE,
        OVERSETTMLD,
        SAMLIV,
        UTBFEILMOT
    }

    enum class Skyld {
        BRUKER,
        IKKE_FORDELT,
        NAV,
        SKYLDDELING
    }
}
