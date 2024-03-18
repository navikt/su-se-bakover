package no.nav.su.se.bakover.service.tilbakekreving

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingUnderRevurderingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingsbehandlingUnderRevurdering
import java.util.UUID

interface TilbakekrevingUnderRevurderingService {
    /**
     * Lagrer et nytt kravgrunnlag vi har mottatt fra Oppdrag.
     */
    fun lagre(
        tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag,
        sessionContext: SessionContext? = null,
    )

    fun hentAvventerKravgrunnlag(sakId: UUID): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun hentAvventerKravgrunnlag(utbetalingId: UUID30): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag?
    fun hentAvventerKravgrunnlag(): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
}

class TilbakekrevingUnderRevurderingServiceImpl(
    private val tilbakekrevingRepo: TilbakekrevingUnderRevurderingRepo,
) : TilbakekrevingUnderRevurderingService {

    override fun lagre(
        tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag,
        sessionContext: SessionContext?,
    ) {
        return tilbakekrevingRepo.lagre(tilbakekrevingsbehandling)
    }

    override fun hentAvventerKravgrunnlag(sakId: UUID): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentAvventerKravgrunnlag(sakId)
    }

    override fun hentAvventerKravgrunnlag(utbetalingId: UUID30): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag? {
        return tilbakekrevingRepo.hentAvventerKravgrunnlag(utbetalingId)
    }

    override fun hentAvventerKravgrunnlag(): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentAvventerKravgrunnlag()
    }
}
