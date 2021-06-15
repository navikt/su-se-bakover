package no.nav.su.se.bakover.domain.grunnlag

// TODO: Del inn i tom og utleda grunnlagsdata. F.eks. ved å bruke NonEmptyList
data class Grunnlagsdata(
    val uføregrunnlag: List<Grunnlag.Uføregrunnlag> = emptyList(),
    val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
    /**
     * Under vilkårsvurdering/opprettet: Kan være null/tom/en/fler. (fler kun ved revurdering)
     * Etter vilkårsvurdering: Skal være en. Senere kan den være fler hvis vi støtter sats per måned.
     * */
    val bosituasjon: List<Grunnlag.Bosituasjon> = emptyList(),
) {
    companion object {
        val EMPTY = Grunnlagsdata()
    }
}

fun List<Grunnlag.Uføregrunnlag>.harForventetInntektStørreEnn0() = this.sumOf { it.forventetInntekt } > 0
fun List<Grunnlag.Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
