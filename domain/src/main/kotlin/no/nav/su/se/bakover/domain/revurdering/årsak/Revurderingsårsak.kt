package no.nav.su.se.bakover.domain.revurdering.årsak

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right

data class Revurderingsårsak(
    val årsak: Årsak,
    val begrunnelse: Begrunnelse,
) {
    data object UgyldigÅrsak
    enum class Årsak {
        MELDING_FRA_BRUKER,
        INFORMASJON_FRA_KONTROLLSAMTALE,
        DØDSFALL,
        ANDRE_KILDER,
        REGULER_GRUNNBELØP,
        MANGLENDE_KONTROLLERKLÆRING,
        MOTTATT_KONTROLLERKLÆRING,
        IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON,

        /*
            Alle disse omgjøringsårsakene her gjelder kun for innvilgede vedtak
         */
        OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
        OMGJØRING_EGET_TILTAK,
        OMGJØRING_KLAGE, // i førsteinstans
        OMGJØRING_TRYGDERETTEN,

        /* Reservert for migrering */
        MIGRERT,
        ;

        companion object {
            fun tryCreate(value: String): Either<UgyldigÅrsak, Årsak> {
                return entries.firstOrNull { it.name == value }?.right() ?: UgyldigÅrsak.left()
            }

            fun hentOmgjøringsEnumer(): String {
                return entries.filter { it.erOmgjøring() }.joinToString(", ") { it.name }
            }
        }
        fun erOmgjøring(): Boolean {
            return when (this) {
                MELDING_FRA_BRUKER, INFORMASJON_FRA_KONTROLLSAMTALE, DØDSFALL, ANDRE_KILDER, REGULER_GRUNNBELØP, MANGLENDE_KONTROLLERKLÆRING, MOTTATT_KONTROLLERKLÆRING, IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON, MIGRERT -> false
                OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN, OMGJØRING_EGET_TILTAK, OMGJØRING_KLAGE, OMGJØRING_TRYGDERETTEN -> true
            }
        }
    }

    sealed interface UgyldigRevurderingsårsak {
        data object UgyldigÅrsak : UgyldigRevurderingsårsak
        data object UgyldigBegrunnelse : UgyldigRevurderingsårsak
    }

    companion object {

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
            ).getOrElse { throw IllegalArgumentException("Ugyldig revurderingsårsak: $it") }
        }
    }

    data class Begrunnelse private constructor(
        val value: String,
    ) {

        override fun toString() = value

        data object KanIkkeVæreTom

        companion object {
            fun tryCreate(value: String): Either<KanIkkeVæreTom, Begrunnelse> {
                return if (value.isBlank()) KanIkkeVæreTom.left() else Begrunnelse(value).right()
            }

            fun create(value: String): Begrunnelse {
                return tryCreate(value).getOrElse { throw IllegalArgumentException("Begrunnelse kan ikke være tom: $value") }
            }

            val MIGRERT = Begrunnelse("MIGRERT")
        }
    }
}
