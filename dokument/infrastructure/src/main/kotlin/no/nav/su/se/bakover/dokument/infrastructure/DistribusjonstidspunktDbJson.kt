package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.Distribusjonstidspunkt

internal enum class DistribusjonstidspunktDbJson {
    UMIDDELBART,
    KJERNETID,
    ;

    fun toDomain(): Distribusjonstidspunkt = when (this) {
        UMIDDELBART -> Distribusjonstidspunkt.UMIDDELBART
        KJERNETID -> Distribusjonstidspunkt.KJERNETID
    }
}

internal fun Distribusjonstidspunkt.toHendelseDbJson(): DistribusjonstidspunktDbJson = when (this) {
    Distribusjonstidspunkt.UMIDDELBART -> DistribusjonstidspunktDbJson.UMIDDELBART
    Distribusjonstidspunkt.KJERNETID -> DistribusjonstidspunktDbJson.KJERNETID
}
