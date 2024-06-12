package no.nav.su.se.bakover.web.routes.sak

import dokument.domain.Distribusjonstype

enum class Distribusjonstype {
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
