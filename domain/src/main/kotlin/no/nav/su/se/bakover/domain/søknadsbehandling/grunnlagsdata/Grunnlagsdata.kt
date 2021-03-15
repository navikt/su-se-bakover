package no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata

data class Grunnlagsdata(
    val uføregrunnlag: List<Uføregrunnlag> = emptyList()
) {
    companion object {
        val EMPTY = Grunnlagsdata()
    }
}
