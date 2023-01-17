package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeOppdatereTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.OppdaterTilbakekrevingsbehandlingRequest
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class OppdaterTilbakekrevingsbehandlingTest {
    @Test
    fun `ugyldig tilstand`() {
        listOf(
            opprettetRevurdering().second,
            beregnetRevurdering().second,
            revurderingTilAttestering().second,
            iverksattRevurdering().second,
        ).forEach { revurdering ->
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn revurdering
                },
            ).let {
                it.revurderingService.oppdaterTilbakekrevingsbehandling(
                    request = OppdaterTilbakekrevingsbehandlingRequest(
                        revurderingId = UUID.randomUUID(),
                        avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.TILBAKEKREV,
                        saksbehandler = saksbehandler,
                    ),
                ) shouldBe KunneIkkeOppdatereTilbakekrevingsbehandling.UgyldigTilstand(
                    fra = revurdering::class,
                ).left()
            }
        }
    }

    @Test
    fun `oppdaterer tilstand for simulert revurdering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering(
                    grunnlagsdataOverrides = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = år(2021),
                            arbeidsinntekt = 5000.0,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ).second
            },
        ).let {
            it.revurderingService.oppdaterTilbakekrevingsbehandling(
                request = OppdaterTilbakekrevingsbehandlingRequest(
                    revurderingId = UUID.randomUUID(),
                    avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.TILBAKEKREV,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().let { oppdatert ->
                (oppdatert as SimulertRevurdering).tilbakekrevingsbehandling shouldBe beOfType<Tilbakekrev>()
            }

            it.revurderingService.oppdaterTilbakekrevingsbehandling(
                request = OppdaterTilbakekrevingsbehandlingRequest(
                    revurderingId = UUID.randomUUID(),
                    avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.IKKE_TILBAKEKREV,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().let { oppdatert ->
                (oppdatert as SimulertRevurdering).tilbakekrevingsbehandling shouldBe beOfType<IkkeTilbakekrev>()
            }
        }
    }
}
