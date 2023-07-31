package no.nav.su.se.bakover.domain.revurdering.brev.tilbakekreving

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.brev.endringInntekt.lagRevurderingInntektDokumentKommando
import no.nav.su.se.bakover.domain.satser.SatsFactory
import java.lang.IllegalArgumentException

/**
 * Ment for internt bruk innenfor revurdering/brev pakken.
 * @throws IllegalArgumentException dersom revurderingen ikke er en tilbakekreving eller dersom man ikke skulle sende brev.
 */
internal fun lagTilbakekrevingDokumentKommando(
    revurdering: Revurdering,
    beregning: Beregning,
    simulering: Simulering,
    satsFactory: SatsFactory,
): IverksettRevurderingDokumentCommand.TilbakekrevingAvPenger {
    require(revurdering.skalTilbakekreve()) {
        "Kan ikke lage tilbakekrevingdokumentkommando for en revurdering som ikke er en tilbakekreving. RevurderingId: ${revurdering.id}"
    }
    return IverksettRevurderingDokumentCommand.TilbakekrevingAvPenger(
        ordinærtRevurderingBrev = lagRevurderingInntektDokumentKommando(
            revurdering = revurdering,
            beregning = beregning,
            satsFactory = satsFactory,
        ),
        tilbakekreving = Tilbakekreving(simulering.hentFeilutbetalteBeløp().månedbeløp),
        satsoversikt = Satsoversikt.fra(revurdering, satsFactory),
    )
}
