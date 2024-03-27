package person.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right

enum class SivilstandTyper {
    UOPPGITT,
    UGIFT,
    GIFT,
    ENKE_ELLER_ENKEMANN,
    SKILT,
    SEPARERT,
    REGISTRERT_PARTNER,
    SEPARERT_PARTNER,
    SKILT_PARTNER,
    GJENLEVENDE_PARTNER,
    ;

    companion object {
        fun fromString(value: String?): Either<UkjentSivilstandtype, SivilstandTyper?> {
            return when (value) {
                "UOPPGITT" -> UOPPGITT.right()
                "UGIFT" -> UGIFT.right()
                "GIFT" -> GIFT.right()
                "ENKE_ELLER_ENKEMANN" -> ENKE_ELLER_ENKEMANN.right()
                "SKILT" -> SKILT.right()
                "SEPARERT" -> SEPARERT.right()
                "REGISTRERT_PARTNER" -> REGISTRERT_PARTNER.right()
                "SEPARERT_PARTNER" -> SEPARERT_PARTNER.right()
                "SKILT_PARTNER" -> SKILT_PARTNER.right()
                "GJENLEVENDE_PARTNER" -> GJENLEVENDE_PARTNER.right()
                null -> null.right()
                else -> UkjentSivilstandtype(value).left()
            }
        }
    }
}

data class UkjentSivilstandtype(val value: String)
