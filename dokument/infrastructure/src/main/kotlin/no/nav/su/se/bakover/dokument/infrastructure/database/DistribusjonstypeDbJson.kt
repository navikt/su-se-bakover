package no.nav.su.se.bakover.dokument.infrastructure.database

import dokument.domain.Distribusjonstype

internal enum class DistribusjonstypeDbJson {
    VEDTAK,
    VIKTIG,
    ANNET,
    ;

    fun toDomain(): Distribusjonstype = when (this) {
        VEDTAK -> Distribusjonstype.VEDTAK
        VIKTIG -> Distribusjonstype.VIKTIG
        ANNET -> Distribusjonstype.ANNET
    }
}

internal fun Distribusjonstype.toHendelseDbJson(): DistribusjonstypeDbJson = when (this) {
    Distribusjonstype.VEDTAK -> DistribusjonstypeDbJson.VEDTAK
    Distribusjonstype.VIKTIG -> DistribusjonstypeDbJson.VIKTIG
    Distribusjonstype.ANNET -> DistribusjonstypeDbJson.ANNET
}
