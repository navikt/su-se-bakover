package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import java.math.BigDecimal

data class Kravgrunnlag(
    val saksnummer: Saksnummer,

    /** Dette er Oppdrag sin ID som er vedlagt i kravgrunnlaget, den er transient i vårt system */
    val vedtakId: String,

    /** Denne er generert av Oppdrag og er vedlagt i kravgrunnlaget, den er transient i vårt system*/
    val kontrollfelt: String,

    /**
     * Saksbehandleren/Attestanten knyttet til vedtaket/utbetalinga.
     * Utbetalinga vår kaller dette behandler, så vi gjenbruker det her.
     * Oppdrag har ikke skillet mellom saksbehandler/attestant (men bruker ofte ordet saksbehandler).
     */
    val behandler: NavIdentBruker,

    val grunnlagsperioder: List<Grunnlagsperiode>,
) {
    data class Grunnlagsperiode(
        val periode: Periode,
        val beløpSkattMnd: BigDecimal,
        val grunnlagsbeløp: List<Grunnlagsbeløp>,
    ) {
        data class Grunnlagsbeløp(
            val kode: KlasseKode,
            val type: KlasseType,
            val beløpTidligereUtbetaling: BigDecimal,
            val beløpNyUtbetaling: BigDecimal,
            val beløpSkalTilbakekreves: BigDecimal,
            val beløpSkalIkkeTilbakekreves: BigDecimal,
            val skatteProsent: BigDecimal,
        )
    }
}
