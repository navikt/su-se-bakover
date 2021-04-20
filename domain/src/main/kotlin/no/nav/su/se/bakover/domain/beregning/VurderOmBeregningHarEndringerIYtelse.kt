package no.nav.su.se.bakover.domain.beregning

data class VurderOmBeregningHarEndringerIYtelse(
    private val tidligereBeregning: Beregning,
    private val nyBeregning: Beregning,
) {
    init {
        assert(tidligereBeregning.getPeriode().inneholder(nyBeregning.getPeriode()))
    }

    val resultat = solve()

    private fun solve(): Boolean {
        val m = tidligereBeregning.getMånedsberegninger().associate { it.getPeriode() to it.getSumYtelse() }

        return nyBeregning.getMånedsberegninger().any { m[it.getPeriode()] != it.getSumYtelse() }
    }
}
