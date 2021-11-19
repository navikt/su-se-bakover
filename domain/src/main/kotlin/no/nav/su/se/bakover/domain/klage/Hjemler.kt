package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right

sealed class Hjemler : List<Hjemmel> {
    abstract val hjemler: List<Hjemmel>

    data class IkkeUtfylt private constructor(
        override val hjemler: List<Hjemmel> = emptyList(),
    ) : Hjemler(), List<Hjemmel> by hjemler {
        companion object {
            fun create(): IkkeUtfylt {
                return IkkeUtfylt()
            }
        }
    }

    data class Utfylt private constructor(
        override val hjemler: NonEmptyList<Hjemmel>,
    ) : Hjemler(), List<Hjemmel> by hjemler {
        companion object {
            /**
             * Kun ment å brukes fra databaselaget og tester
             */
            fun create(hjemler: NonEmptyList<Hjemmel>): Utfylt {
                return tryCreate(hjemler).getOrHandle {
                    throw IllegalStateException(it.toString())
                }
            }

            fun tryCreate(hjemler: NonEmptyList<Hjemmel>): Either<KunneIkkeLageHjemler, Utfylt> {
                return if (hjemler == hjemler.distinct()) {
                    Utfylt(hjemler).right()
                } else {
                    KunneIkkeLageHjemler.left()
                }
            }
        }
    }

    object KunneIkkeLageHjemler
}

/**
 * Dato er den siste datoen for når loven ble endret eller tredde i kraft for SU ufør.
 * Første gyldige dato er 2021-01-01 for SU ufør.
 *
 * TODO jah: Flytt disse ut i et eget lov/paragraf konsept (utenfor klage). Bør kunne gjenbrukes for referanser til loven i søknadsbehandling og revurderinger.
 */
enum class Hjemmel {
    /** Kapittel 2 - § 3.Kven som kan få stønad (2021-01-01) */
    SU_PARAGRAF_3,

    /** Kapittel 2 - § 4.Opphald i utlandet (2021-01-01) */
    SU_PARAGRAF_4,

    /** Kapittel 3 - § 5.Full supplerande stønad (2021-01-01) */
    SU_PARAGRAF_5,

    /** Kapittel 3 - § 6.Inntekt som går til frådrag i supplerande stønad (2021-01-01) */
    SU_PARAGRAF_6,

    /** Kapittel 3 - § 8.Formue (2021-01-01) */
    SU_PARAGRAF_8,

    /** Kapittel 3 - § 9.Låge stønadsbeløp (2021-01-01) */
    SU_PARAGRAF_9,

    /** Kapittel 3 - § 10.Endringar (2021-01-01) */
    SU_PARAGRAF_10,

    /** Kapittel 3 - § 12.Opphald i institusjon mv. (2021-01-01) */
    SU_PARAGRAF_12,

    /** Kapittel 3 - § 13.Tilbakekrevjing (2021-01-01) */
    SU_PARAGRAF_13,

    /** Kapittel 4 - § 18.Plikt til å gi opplysningar (2021-01-01) */
    SU_PARAGRAF_18;
}
