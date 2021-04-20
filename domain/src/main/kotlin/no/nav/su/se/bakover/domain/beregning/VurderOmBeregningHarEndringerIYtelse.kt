package no.nav.su.se.bakover.domain.beregning

data class VurderOmBeregningHarEndringerIYtelse(
    private val tidligereBeregning: Beregning,
    private val nyBeregning: Beregning,
) {
    // TODO: Må endres når vi åpner opp for å kunne revurdere på tvers av vedtak
    init {
        assert(tidligereBeregning.getPeriode().inneholder(nyBeregning.getPeriode()))
    }

    val resultat by lazy {
        val m = tidligereBeregning.getMånedsberegninger().associate { it.getPeriode() to it.getSumYtelse() }

        nyBeregning.getMånedsberegninger().any { m[it.getPeriode()] != it.getSumYtelse() }
    }
}
