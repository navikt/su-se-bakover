package no.nav.su.se.bakover.domain.grunnlag

data class Grunnlagsdata(
    val uføregrunnlag: List<Grunnlag.Uføregrunnlag> = emptyList()
) {
    companion object {
        val EMPTY = Grunnlagsdata()
    }
}
