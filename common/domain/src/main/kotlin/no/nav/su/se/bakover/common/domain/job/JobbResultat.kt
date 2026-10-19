package no.nav.su.se.bakover.common.domain.job

/**
 * Resultat fra en jobbkjøring. Returneres av jobb-lambdaen til [startStoppableJob].
 */
sealed interface JobbResultat {
    data object Ok : JobbResultat
    data class DelvisFeilet(val melding: String) : JobbResultat
}
