package no.nav.su.se.bakover.domain.revurdering

import beregning.domain.fradrag.FradragTilhører
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import org.junit.jupiter.api.Test

class RevurderingSimulerTest {

    @Test
    fun `oppretter tilbakekrevingsbehandling dersom simulering inneholder feilutbetaling som ikke skyldes utlandsopphold`() {
        val clock = TikkendeKlokke(1.august(2021).fixedClock())
        beregnetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mars(2021),
                    arbeidsinntekt = 15799.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            clock = clock,
        ).let { (sak, beregnet) ->
            beregnet.shouldBeType<BeregnetRevurdering.Innvilget>().also {
                it.simuler(
                    saksbehandler = saksbehandler,
                    clock = fixedClock,
                    skalUtsetteTilbakekreving = false,
                    simuler = { _, _ ->
                        simulerUtbetaling(
                            sak = sak,
                            revurdering = beregnet,
                            clock = clock,
                        ).map {
                            it.simulering
                        }
                    },
                ).getOrFail().tilbakekrevingsbehandling.shouldBeType<IkkeAvgjort>()
            }
        }
    }
}
