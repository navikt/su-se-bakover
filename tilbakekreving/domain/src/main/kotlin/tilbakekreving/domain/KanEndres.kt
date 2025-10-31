package tilbakekreving.domain

import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag

/**
 * En supertype for de tilstandene vi kan endre behandlingen:
 * - forhåndsvarsle
 * - vurdere
 * - oppdatere vedtaksbrev
 * - oppdatere notat
 * - oppdatere kravgrunnlag
 * - annullere kravgrunnlag
 */
sealed interface KanEndres : Tilbakekrevingsbehandling {
    override fun erÅpen() = true
}

sealed interface KanEndresHarKravgrunnlag : KanEndres {
    override fun erÅpen() = true
    override val kravgrunnlag: Kravgrunnlag
}
