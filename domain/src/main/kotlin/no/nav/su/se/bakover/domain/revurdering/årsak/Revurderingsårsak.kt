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
        STANSET_VED_EN_FEIL,
        IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON,

        /*
            Alle disse omgjøringsårsakene her gjelder kun for innvilgede vedtak
         */
        OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
        OMGJØRING_EGET_TILTAK,
        OMGJØRING_KLAGE, // i førsteinstans, som betyr at man kun behandler klagen i vedtaksenheten Ålseund alstså su-se-bakover
        OMGJØRING_TRYGDERETTEN,

        /* Reservert for migrering */
        MIGRERT,
        ;

        companion object {
            fun tryCreate(value: String): Either<UgyldigÅrsak, Årsak> {
                return entries.firstOrNull { it.name == value }?.right() ?: UgyldigÅrsak.left()
            }

            // For IN query i DB
            fun hentOmgjøringsEnumer(): String {
                return entries.filter { it.erOmgjøring() }.joinToString(", ") { "'${it.name}'" }
            }
        }
        fun erOmgjøring(): Boolean {
            return when (this) {
                MELDING_FRA_BRUKER, INFORMASJON_FRA_KONTROLLSAMTALE, DØDSFALL, ANDRE_KILDER, REGULER_GRUNNBELØP, MANGLENDE_KONTROLLERKLÆRING, MOTTATT_KONTROLLERKLÆRING, STANSET_VED_EN_FEIL, IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON, MIGRERT -> false
                OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN, OMGJØRING_EGET_TILTAK, OMGJØRING_KLAGE, OMGJØRING_TRYGDERETTEN -> true
            }
        }
    }

    sealed interface UgyldigRevurderingsårsak {
        data object UgyldigÅrsak : UgyldigRevurderingsårsak
    }

    companion object {

        fun tryCreate(årsak: String, begrunnelse: String): Either<UgyldigRevurderingsårsak, Revurderingsårsak> {
            val validÅrsak = Årsak.tryCreate(årsak).getOrElse {
                return UgyldigRevurderingsårsak.UgyldigÅrsak.left()
            }
            val validBegrunnelse = Begrunnelse.tryCreate(begrunnelse)
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

        companion object {
            fun tryCreate(value: String): Begrunnelse {
                return Begrunnelse(value)
            }

            fun create(value: String): Begrunnelse {
                return tryCreate(value)
            }
        }
    }
}
