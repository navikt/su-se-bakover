package no.nav.su.se.bakover.domain.revurdering.tilbakekreving

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import java.util.UUID

/**
 * Historisk type. I databasetabellen "revurdering_tilbakekreving" har vi bare én tilstand igjen: "sendt_tilbakekrevingsvedtak".
 * Vi har ikke lenger "under_behandling" (slettet manuelt), "avventer_kravgrunnlag" (naturlig prosessert) og "mottatt_kravgrunnlag" (naturlig prosessert).
 *
 * Denne ble brukt for å ta stilling til om vi skulle tilbakekreve eller ikke under revurderingen.
 * Dette er nå flyttet til en egen behandling.
 * Vi bevarer alle de vi har sendt til Tilbakekrevingskomponenten ved Oppdrag.
 *
 * @property periode Vi støttet kun en periode da vi hadde tilbakekreving under revurdering.
 */
data class HistoriskSendtTilbakekrevingsvedtak(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val revurderingId: RevurderingId,
    val periode: Periode,
    val kravgrunnlag: Kravgrunnlag,
    val kravgrunnlagMottatt: Tidspunkt,
    val tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
    val avgjørelse: AvgjørelseTilbakekrevingUnderRevurdering,
) {
    enum class AvgjørelseTilbakekrevingUnderRevurdering {
        Tilbakekrev,
        IkkeTilbakekrev,
    }
}
