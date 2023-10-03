package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import java.math.BigDecimal

data class Kravgrunnlag(
    val saksnummer: Saksnummer,
    /** Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system. */
    val eksternKravgrunnlagId: String,

    /** Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system. */
    val eksternVedtakId: String,

    /** Denne er generert av Oppdrag og er vedlagt i kravgrunnlaget, den er transient i vårt system*/
    val eksternKontrollfelt: String,
    val status: KravgrunnlagStatus,

    /**
     * Saksbehandleren/Attestanten knyttet til vedtaket/utbetalinga.
     * Utbetalinga vår kaller dette behandler, så vi gjenbruker det her.
     * Oppdrag har ikke skillet mellom saksbehandler/attestant (men bruker ofte ordet saksbehandler).
     */
    val behandler: String,
    val utbetalingId: UUID30,
    val grunnlagsperioder: List<Grunnlagsmåned>,
) {

    fun hentBeløpSkalTilbakekreves(): Månedsbeløp {
        return Månedsbeløp(
            grunnlagsperioder
                .map { it.hentBeløpSkalTilbakekreves() }
                .filter { it.sum() > 0 },
        )
    }

    data class Grunnlagsmåned(
        val måned: Måned,
        val beløpSkattMnd: BigDecimal,
        val grunnlagsbeløp: List<Grunnlagsbeløp>,
    ) {

        fun hentBeløpSkalTilbakekreves(): MånedBeløp {
            return MånedBeløp(
                måned.tilMåned(),
                Beløp(grunnlagsbeløp.sumOf { it.beløpSkalTilbakekreves.intValueExact() }),
            )
        }

        data class Grunnlagsbeløp(
            // TODO jah: kode/type, må vi ta høyde for alle variantene vi kan få her? (vi får bare et begrenset sett) ref. dok.
            val kode: KlasseKode,
            val type: KlasseType,
            val beløpTidligereUtbetaling: BigDecimal,
            val beløpNyUtbetaling: BigDecimal,
            val beløpSkalTilbakekreves: BigDecimal,
            val beløpSkalIkkeTilbakekreves: BigDecimal,
            val skatteProsent: BigDecimal,
        )
    }

    enum class KravgrunnlagStatus {
        Annulert,

        /** Kommentar jah: Gjetter på omg står for omgjøring. */
        AnnulertVedOmg,
        Avsluttet,
        Ferdigbehandlet,
        Endret,
        Feil,
        Manuell,
        Nytt,
        Sperret,
    }
}
