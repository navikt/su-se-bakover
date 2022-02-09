package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface TilbakekrevingRepo {
    fun lagreMottattKravgrunnlag(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag)
    fun lagreKravgrunnlagBesvart(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.KravgrunnlagBesvart)
    fun hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag>
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun defaultTransactionContext(): TransactionContext
}
