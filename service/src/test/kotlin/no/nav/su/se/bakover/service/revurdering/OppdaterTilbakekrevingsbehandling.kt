package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.BurdeForstått
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Forsto
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.KunneIkkeForstå
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class OppdaterTilbakekrevingsbehandling {
    @Test
    fun `fant ikke revurdering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.revurderingService.oppdaterTilbakekrevingsbehandling(
                request = OppdaterTilbakekrevingsbehandlingRequest(
                    revurderingId = UUID.randomUUID(),
                    avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.FORSTO,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeOppdatereTilbakekrevingsbehandling.FantIkkeRevurdering.left()
        }
    }

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
                        avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.FORSTO,
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
                            periode = periode2021,
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
                    avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.FORSTO,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().let { oppdatert ->
                oppdatert.tilbakekrevingsbehandling shouldBe beOfType<Forsto>()
            }

            it.revurderingService.oppdaterTilbakekrevingsbehandling(
                request = OppdaterTilbakekrevingsbehandlingRequest(
                    revurderingId = UUID.randomUUID(),
                    avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.BURDE_FORSTÅTT,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().let { oppdatert ->
                oppdatert.tilbakekrevingsbehandling shouldBe beOfType<BurdeForstått>()
            }

            it.revurderingService.oppdaterTilbakekrevingsbehandling(
                request = OppdaterTilbakekrevingsbehandlingRequest(
                    revurderingId = UUID.randomUUID(),
                    avgjørelse = OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.KUNNE_IKKE_FORSTÅ,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().let { oppdatert ->
                oppdatert.tilbakekrevingsbehandling shouldBe beOfType<KunneIkkeForstå>()
            }
        }
    }
}
