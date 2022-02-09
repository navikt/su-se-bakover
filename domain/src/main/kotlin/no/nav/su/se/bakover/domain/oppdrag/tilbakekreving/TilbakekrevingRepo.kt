package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface TilbakekrevingRepo {
    fun lagreMottattKravgrunnlag(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag)
    fun hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag>
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag>
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag>
    fun defaultTransactionContext(): TransactionContext
}
