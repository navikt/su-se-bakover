package no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface TilbakekrevingUnderRevurderingRepo {
    fun lagre(
        tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag,
        sessionContext: SessionContext? = null,
    )
    fun lagre(tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentMottattKravgrunnlag(): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag>
    fun hentAvventerKravgrunnlag(sakId: UUID): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun hentAvventerKravgrunnlag(utbetalingId: UUID30): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag?
    fun hentAvventerKravgrunnlag(): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun defaultTransactionContext(): TransactionContext
}
