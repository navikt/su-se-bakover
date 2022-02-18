package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
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
    return simulering.tolk().let {
        Kravgrunnlag(
            saksnummer = revurdering.saksnummer,
            kravgrunnlagId = "123456",
            vedtakId = "654321",
            kontrollfelt = Tidspunkt.now(clock).toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            utbetalingId = utbetalingId,
            grunnlagsperioder = it.simulertePerioder
                .filter { it.harFeilutbetalinger() }
                .map { periode ->
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = Periode.create(
                            fraOgMed = periode.periode.fraOgMed,
                            tilOgMed = periode.periode.tilOgMed,
                        ),
                        beløpSkattMnd = BigDecimal(4395),
                        grunnlagsbeløp = listOf(
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.KL_KODE_FEIL_INNT,
                                type = KlasseType.FEIL,
                                beløpTidligereUtbetaling = BigDecimal.ZERO,
                                beløpNyUtbetaling = BigDecimal(periode.hentFeilutbetalteBeløp().sum()),
                                beløpSkalTilbakekreves = BigDecimal.ZERO,
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal.ZERO,
                            ),
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.SUUFORE,
                                type = KlasseType.YTEL,
                                beløpTidligereUtbetaling = BigDecimal(periode.hentUtbetaltBeløp().sum()),
                                beløpNyUtbetaling = BigDecimal(periode.hentØnsketUtbetaling().sum()),
                                beløpSkalTilbakekreves = BigDecimal(periode.hentFeilutbetalteBeløp().sum()),
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal(43.9983),
                            ),
                        ),
                    )
                },
        )
    }
}
