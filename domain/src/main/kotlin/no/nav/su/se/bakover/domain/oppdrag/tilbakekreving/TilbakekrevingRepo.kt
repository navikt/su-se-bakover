package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import java.util.UUID

interface TilbakekrevingRepo {
    fun lagreKravgrunnlag(kravgrunnlag: RåttKravgrunnlag)
    fun hentUbehandlaKravgrunnlag(): List<RåttKravgrunnlag>
    fun hentUoversendteTilbakekrevingsavgjørelser(sakId: UUID): List<Tilbakekrevingsavgjørelse>
    fun lagreTilbakekrevingsavgjørelse(tilbakekrevingsavgjørelse: Tilbakekrevingsavgjørelse)
}
