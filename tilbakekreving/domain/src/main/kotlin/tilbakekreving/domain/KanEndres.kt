package tilbakekreving.domain

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
