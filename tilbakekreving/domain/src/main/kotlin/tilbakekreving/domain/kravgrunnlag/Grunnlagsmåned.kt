package tilbakekreving.domain.kravgrunnlag

import arrow.core.Nel
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.math.BigDecimal

/**
 * Antagelse/empiri: Siden vi sender utbetalingslinjer med månedlig utbetalingsfrekvens, mottar vi også kravgrunnlag med månedlig utbetalingsfrekvens.
 * @param betaltSkattForYtelsesgruppen Totalt betalt skatt for ytelsesgruppen i måneden. Dvs. dette trenger ikke bare gjelde ytelsen vi har kravgrunnlag for, men også andre ytelser som utbetales samtidig. Det eneste vi bruker det til er å validere at vi setter skatten høyere enn dette.
 * @param grunnlagsbeløp Hver måned kan ha
 */
data class Grunnlagsmåned(
    val måned: Måned,
    val betaltSkattForYtelsesgruppen: BigDecimal,
    // TODO jah: Vurder om vi skal splitte denne opp i 2 lister, en for SUUFORE/YTEL og den andre for KL_KODE_FEIL_INNT/FEIL. Usikker på om vi kan få flere varianter, men det kan vi kode inn senere?
    val grunnlagsbeløp: Nel<Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp>,
)
