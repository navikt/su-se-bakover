package no.nav.su.se.bakover.domain.grunnlag

data class Grunnlagsdatasett(
    /** Sammensmelting av vedtakene før revurderingen. Det som lå til grunn for revurderingen */
    val førBehandling: Grunnlagsdata,
    /** De endringene som er lagt til i revurderingen (denne oppdateres ved lagring) */
    val endring: Grunnlagsdata,
    /** Sammensmeltinga av førBehandling og endring - denne er ikke persistert  */
    val resultat: Grunnlagsdata,
)

data class Grunnlagsdata(
    val uføregrunnlag: List<Grunnlag.Uføregrunnlag> = emptyList()
) {
    companion object {
        val EMPTY = Grunnlagsdata()
    }
}
