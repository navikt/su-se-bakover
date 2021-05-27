package no.nav.su.se.bakover.domain.grunnlag

// TODO: Del inn i tom og utleda grunnlagsdata. F.eks. ved å bruke NonEmptyList
data class Grunnlagsdata(
    val uføregrunnlag: List<Grunnlag.Uføregrunnlag> = emptyList(),
    val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList()
) {
    companion object {
        val EMPTY = Grunnlagsdata()
    }

    /** TODO: Legg i Utleda klassen med NEL */
    fun hentNyesteUføreGrunnlag(): Grunnlag.Uføregrunnlag = uføregrunnlag.maxByOrNull { it.opprettet.instant }!!
}

fun List<Grunnlag.Uføregrunnlag>.harForventetInntektStørreEnn0() = this.sumOf { it.forventetInntekt } > 0
