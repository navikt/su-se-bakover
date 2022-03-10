package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.matchendeKravgrunnlag
import no.nav.su.se.bakover.test.periodeMai2021
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

internal class TilbakekrevingsbehandlingTest {
    @Test
    fun `tilbakekrevingsvedtak for full tilbakekreving`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periodeMai2021,
                    arbeidsinntekt = 12500.0,
                ),
            ),
        )
        val kravgrunnlag = matchendeKravgrunnlag(
            revurdering = revurdering,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling!!.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = Tilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = RåttKravgrunnlag(kravgrunnlag.toString()),
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak { kravgrunnlag } shouldBe Tilbakekrevingsvedtak.FullTilbakekreving(
            vedtakId = "654321",
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.kontrollfelt,
            behandler = NavIdentBruker.Saksbehandler("K231B433"), // TODO sannsynligvis tilbakekrevingsbehandling/revurdering
            tilbakekrevingsperioder = listOf(
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = periodeMai2021,
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    tilbakekrevingsbeløp = listOf(
                        Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
                            kodeKlasse = KlasseKode.KL_KODE_FEIL_INNT,
                            beløpTidligereUtbetaling = BigDecimal.ZERO,
                            beløpNyUtbetaling = BigDecimal("12500"),
                            beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                            beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                        ),
                        Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                            kodeKlasse = KlasseKode.SUUFORE,
                            beløpTidligereUtbetaling = BigDecimal("21989"),
                            beløpNyUtbetaling = BigDecimal("9489"),
                            beløpSomSkalTilbakekreves = BigDecimal("12500"),
                            beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                            beløpSkatt = BigDecimal("4395"),
                            tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING,
                            skyld = Tilbakekrevingsvedtak.Skyld.BRUKER,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `justerer størrelsen på beløp for skatt i henhold til skatteprosent - beløp lavere enn total betalt skatt`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periodeMai2021,
                    arbeidsinntekt = 4000.0,
                ),
            ),
        )
        val kravgrunnlag = matchendeKravgrunnlag(
            revurdering = revurdering,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling!!.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = Tilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = RåttKravgrunnlag(kravgrunnlag.toString()),
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak { kravgrunnlag }.let { tilbakekrevingsvedtak ->
            tilbakekrevingsvedtak.tilbakekrevingsperioder
                .flatMap { it.tilbakekrevingsbeløp }
                .filterIsInstance<Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse>()
                .all { it.beløpSkatt == BigDecimal("1759") && it.beløpSkatt < kravgrunnlag.grunnlagsperioder[0].beløpSkattMnd } shouldBe true
        }
    }

    @Test
    fun `justerer størrelsen på beløp for skatt i henhold til skatteprosent - beløp høyere enn total betalt skatt`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periodeMai2021,
                    arbeidsinntekt = 20000.0,
                ),
            ),
        )
        val kravgrunnlag = matchendeKravgrunnlag(
            revurdering = revurdering,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling!!.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = Tilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = RåttKravgrunnlag(kravgrunnlag.toString()),
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak { kravgrunnlag }.let { tilbakekrevingsvedtak ->
            tilbakekrevingsvedtak.tilbakekrevingsperioder
                .flatMap { it.tilbakekrevingsbeløp }
                .filterIsInstance<Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse>()
                .all { it.beløpSkatt == BigDecimal("4395") } shouldBe true
        }
    }

    @Test
    fun `tilbakekrevingsvedtak for ingen tilbakekreving`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periodeMai2021,
                    arbeidsinntekt = 12500.0,
                ),
            ),
        )
        val kravgrunnlag = matchendeKravgrunnlag(
            revurdering = revurdering,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling!!.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = IkkeTilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = RåttKravgrunnlag(kravgrunnlag.toString()),
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak { kravgrunnlag } shouldBe Tilbakekrevingsvedtak.IngenTilbakekreving(
            vedtakId = "654321",
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.kontrollfelt,
            behandler = NavIdentBruker.Saksbehandler("K231B433"), // TODO sannsynligvis tilbakekrevingsbehandling/revurdering
            tilbakekrevingsperioder = listOf(
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = periodeMai2021,
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    tilbakekrevingsbeløp = listOf(
                        Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
                            kodeKlasse = KlasseKode.KL_KODE_FEIL_INNT,
                            beløpTidligereUtbetaling = BigDecimal.ZERO,
                            beløpNyUtbetaling = BigDecimal("12500"),
                            beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                            beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                        ),
                        Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                            kodeKlasse = KlasseKode.SUUFORE,
                            beløpTidligereUtbetaling = BigDecimal("21989"),
                            beløpNyUtbetaling = BigDecimal("9489"),
                            beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                            beløpSomIkkeTilbakekreves = BigDecimal("12500"),
                            beløpSkatt = BigDecimal.ZERO,
                            tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING,
                            skyld = Tilbakekrevingsvedtak.Skyld.IKKE_FORDELT,
                        ),
                    ),
                ),
            ),
        )
    }
}
