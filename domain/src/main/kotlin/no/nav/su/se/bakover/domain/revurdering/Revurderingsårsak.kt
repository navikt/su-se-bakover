package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right

data class Revurderingsårsak(
    val årsak: Årsak,
    val begrunnelse: Begrunnelse,
) {
    object UgyldigÅrsak
    enum class Årsak {
        MELDING_FRA_BRUKER,
        INFORMASJON_FRA_KONTROLLSAMTALE,
        DØDSFALL,
        ANDRE_KILDER,
        REGULER_GRUNNBELØP,
        MANGLENDE_KONTROLLERKLÆRING,
        MOTTATT_KONTROLLERKLÆRING,

        /* Reservert for migrering */
        MIGRERT;

        companion object {
            fun tryCreate(value: String): Either<UgyldigÅrsak, Årsak> {
                return values().firstOrNull { it.name == value }?.right() ?: UgyldigÅrsak.left()
            }
        }
    }

    sealed class UgyldigRevurderingsårsak {
        object UgyldigÅrsak : UgyldigRevurderingsårsak()
        object UgyldigBegrunnelse : UgyldigRevurderingsårsak()
    }

    companion object {
        val MIGRERT = Revurderingsårsak(Årsak.MIGRERT, Begrunnelse.MIGRERT)

        fun tryCreate(årsak: String, begrunnelse: String): Either<UgyldigRevurderingsårsak, Revurderingsårsak> {
            val validÅrsak = Årsak.tryCreate(årsak).getOrElse {
                return UgyldigRevurderingsårsak.UgyldigÅrsak.left()
            }
            val validBegrunnelse = Begrunnelse.tryCreate(begrunnelse).getOrElse {
                return UgyldigRevurderingsårsak.UgyldigBegrunnelse.left()
            }
            return Revurderingsårsak(validÅrsak, validBegrunnelse).right()
        }

        fun create(årsak: String, begrunnelse: String): Revurderingsårsak {
            return tryCreate(
                årsak = årsak,
                begrunnelse = begrunnelse,
            ).getOrHandle { throw IllegalArgumentException("Ugyldig revurderingsårsak: $it") }
        }
    }

    data class Begrunnelse private constructor(
        val value: String,
    ) {

        override fun toString() = value

        object KanIkkeVæreTom

        companion object {
            fun tryCreate(value: String): Either<KanIkkeVæreTom, Begrunnelse> {
                return if (value.isBlank()) KanIkkeVæreTom.left() else Begrunnelse(value).right()
            }

            fun create(value: String): Begrunnelse {
                return tryCreate(value).getOrHandle { throw IllegalArgumentException("Begrunnelse kan ikke være tom: $value") }
            }

            val MIGRERT = Begrunnelse("MIGRERT")
        }
    }
}
