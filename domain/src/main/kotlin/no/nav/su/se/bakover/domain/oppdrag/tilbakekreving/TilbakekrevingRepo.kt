package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface TilbakekrevingRepo {
    fun lagre(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag)
    fun lagre(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentMottattKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag>
    fun hentAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun hentAvventerKravgrunnlag(utbetalingId: UUID30): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag?
    fun hentAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun defaultTransactionContext(): TransactionContext
}
