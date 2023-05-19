package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import java.math.BigDecimal
import java.time.Clock

/**
 * TODO dobbeltimplementasjon
 * @see [no.nav.su.se.bakover.web.services.tilbakekreving.matchendeKravgrunnlag]
 */
fun matchendeKravgrunnlag(
    revurdering: Revurdering,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): Kravgrunnlag {
    return simulering.let {
        Kravgrunnlag(
            saksnummer = revurdering.saksnummer,
            kravgrunnlagId = "123456",
            vedtakId = "654321",
            kontrollfelt = Tidspunkt.now(clock).toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            utbetalingId = utbetalingId,
            grunnlagsperioder = it.hentFeilutbetalteBeløp()
                .map { (periode, feilutbetaling) ->
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = periode.tilMåned(),
                        beløpSkattMnd = BigDecimal(4395),
                        grunnlagsbeløp = listOf(
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.KL_KODE_FEIL_INNT,
                                type = KlasseType.FEIL,
                                beløpTidligereUtbetaling = BigDecimal.ZERO,
                                beløpNyUtbetaling = BigDecimal(feilutbetaling.sum()),
                                beløpSkalTilbakekreves = BigDecimal.ZERO,
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal.ZERO,
                            ),
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.SUUFORE,
                                type = KlasseType.YTEL,
                                beløpTidligereUtbetaling = BigDecimal(it.hentUtbetalteBeløp(periode)!!.sum()),
                                beløpNyUtbetaling = BigDecimal(it.hentTotalUtbetaling(periode)!!.sum()),
                                beløpSkalTilbakekreves = BigDecimal(feilutbetaling.sum()),
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal("43.9983"),
                            ),
                        ),
                    )
                },
        )
    }
}
