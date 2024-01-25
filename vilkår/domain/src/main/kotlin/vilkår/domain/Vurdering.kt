package vilkår.domain

/**
 * En [Vurderingsperiode] og et [Vilkår] har en [Vurdering] som er basert på grunnlagene (automatisk/maskinell vurdering) eller den/de manuelle vurderingene som er gjort tilknyttet dette [Vilkår].
 *
 * Må ikke forveksles med [Vilkårsvurderingsresultat] som er det tilsvarende vurderingsresultatet som er gjort på tvers av _alle_ vilkårene.
 */
sealed interface Vurdering {
    data object Avslag : Vurdering
    data object Innvilget : Vurdering
    data object Uavklart : Vurdering
}
