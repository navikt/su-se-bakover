package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

interface TilbakekrevingRepo {
    fun lagreKravgrunnlag(kravgrunnlag: RåttKravgrunnlag)
    fun hentUbehandlaKravgrunnlag(): List<RåttKravgrunnlag>
    fun hentIkkeOversendteTilbakekrevingsbehandlinger(sakId: UUID): List<Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort>
    fun hentTilbakekrevingsbehandling(saksnummer: Saksnummer): Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort
    fun defaultTransactionContext(): TransactionContext
}
