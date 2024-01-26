package no.nav.su.se.bakover.database.grunnlag

import vilkår.common.domain.Vurdering

internal enum class ResultatDto {
    AVSLAG,
    INNVILGET,
    UAVKLART,
    ;

    fun toDomain() = when (this) {
        AVSLAG -> Vurdering.Avslag
        INNVILGET -> Vurdering.Innvilget
        UAVKLART -> Vurdering.Uavklart
    }
}

internal fun Vurdering.toDto() = when (this) {
    Vurdering.Avslag -> ResultatDto.AVSLAG.toString()
    Vurdering.Innvilget -> ResultatDto.INNVILGET.toString()
    Vurdering.Uavklart -> ResultatDto.UAVKLART.toString()
}
