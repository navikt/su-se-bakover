package no.nav.su.se.bakover.web.services.tilbakekreving

import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.requireType
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vedtakRevurdering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class TilbakekrevingConsumerTest {
    @Test
    fun `kaster exception dersom mapping av kravgrunnlag feiler`() {
        TilbakekrevingConsumer(
            tilbakekrevingService = mock(),
            revurderingService = mock(),
            clock = fixedClock,
        ).let {
            assertThrows<IllegalArgumentException> {
                it.onMessage("""<bogus>xml</bogus>""")
            }.also {
                it.message shouldContain "Ukjent meldingstype"
            }
        }
    }

    @Test
    fun `kaster exception dersom det ikke eksisterer tilbakekrevingsbehandling som avventer kravgrunnlag`() {
        TilbakekrevingConsumer(
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID30>()) } doReturn null
            },
            revurderingService = mock(),
            clock = fixedClock,
        ).let {
            assertThrows<IllegalStateException> {
                it.onMessage(javaClass.getResource("/tilbakekreving/kravgrunnlag_opphør.xml").readText())
            }.also {
                it.message shouldContain "Forventet å finne 1 tilbakekrevingsbehandling som avventer kravgrunnlag for utbetalingId"
            }
        }
    }

    @Test
    fun `kaster exception dersom kravgrunnlag ikke stemmer overens med tilbakekrevingsbehandlingen`() {
        val (_, vedtak) = vedtakRevurdering(
            clock = tikkendeFixedClock(),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt1000(periode = år(2021)),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        ).let {
            requireType<Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>>(it)
        }

        TilbakekrevingConsumer(
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID30>()) } doReturn vedtak.behandling.tilbakekrevingsbehandling as Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag
            },
            revurderingService = mock {
                on { hentRevurdering(any()) } doReturn vedtak.behandling
            },
            clock = fixedClock,
        ).let {
            assertThrows<IllegalStateException> {
                it.onMessage(javaClass.getResource("/tilbakekreving/kravgrunnlag_opphør.xml").readText())
            }.also {
                it.message shouldContain "Ikke samsvar mellom perioder og beløp"
            }
        }
    }

    @Test
    fun `happy path`() {
        val (_, vedtak) = vedtakRevurdering(
            clock = tikkendeFixedClock(),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt1000(periode = år(2021)),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        ).let {
            requireType<Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>>(it)
        }

        TilbakekrevingConsumer(
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID30>()) } doReturn vedtak.behandling.tilbakekrevingsbehandling as Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag
            },
            revurderingService = mock {
                on { hentRevurdering(any()) } doReturn vedtak.behandling
            },
            clock = fixedClock,
        ).let {
            it.onMessage(
                TilbakekrevingsmeldingMapper.toXml(
                    matchendeKravgrunnlagDto(
                        revurdering = vedtak.behandling,
                        simulering = vedtak.simulering,
                        utbetalingId = vedtak.utbetalingId,
                        clock = fixedClock,
                    ),
                ).getOrFail(),
            )
        }
    }
}
