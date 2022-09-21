package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right

sealed interface Hjemler : List<Hjemmel> {

    companion object {
        fun empty(): IkkeUtfylt {
            return IkkeUtfylt.create()
        }

        fun tryCreate(hjemler: List<Hjemmel>): Either<KunneIkkeLageHjemler, Hjemler> {
            return if (hjemler.isEmpty()) {
                empty().right()
            } else {
                Utfylt.tryCreate(NonEmptyList.fromListUnsafe(hjemler))
            }
        }
    }

    data class IkkeUtfylt private constructor(
        private val hjemler: List<Hjemmel> = emptyList(),
    ) : Hjemler, List<Hjemmel> by hjemler {
        companion object {
            fun create(): IkkeUtfylt {
                return IkkeUtfylt()
            }
        }
    }

    /**
     * Hjemlene blir sortert alfabetisk.
     */
    data class Utfylt private constructor(
        private val hjemler: NonEmptyList<Hjemmel>,
    ) : Hjemler, List<Hjemmel> by hjemler {
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
                return if (hjemler.toList() == hjemler.distinct()) {
                    Utfylt(NonEmptyList.fromListUnsafe(hjemler.sorted())).right()
                } else {
                    KunneIkkeLageHjemler.left()
                }
            }
        }
    }

    object KunneIkkeLageHjemler
}

/**
 * Dato er den siste datoen for når loven ble endret eller trådte i kraft for SU ufør.
 * Første gyldige dato er 2021-01-01 for SU ufør.
 *
 * TODO jah: Flytt disse ut i et eget lov/paragraf konsept (utenfor klage). Bør kunne gjenbrukes for referanser til loven i søknadsbehandling og revurderinger.
 */
enum class Hjemmel(val lov: Lov, val kapittel: Int, val paragrafnummer: Int) {
    /** Kapittel 2 - § 3.Kven som kan få stønad (2021-01-01) */
    SU_PARAGRAF_3(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 2, 3),

    /** Kapittel 2 - § 4.Opphald i utlandet (2021-01-01) */
    SU_PARAGRAF_4(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 2, 4),

    /** Kapittel 3 - § 5.Full supplerande stønad (2021-01-01) */
    SU_PARAGRAF_5(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 5),

    /** Kapittel 3 - § 6.Inntekt som går til frådrag i supplerande stønad (2021-01-01) */
    SU_PARAGRAF_6(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 6),

    /** Kapittel 3 - § 7.Utmåling av supplerande stønad (2021-01-01) */
    SU_PARAGRAF_7(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 7),

    /** Kapittel 3 - § 8.Formue (2021-01-01) */
    SU_PARAGRAF_8(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 8),

    /** Kapittel 3 - § 9.Låge stønadsbeløp (2021-01-01) */
    SU_PARAGRAF_9(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 9),

    /** Kapittel 3 - § 10.Endringar (2021-01-01) */
    SU_PARAGRAF_10(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 10),

    /** Kapittel 3 - § 11.Stønadsperiode og utbetaling (2021-01-01) */
    SU_PARAGRAF_11(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 11),

    /** Kapittel 3 - § 12.Opphald i institusjon mv. (2021-01-01) */
    SU_PARAGRAF_12(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 12),

    /** Kapittel 3 - § 13.Tilbakekrevjing (2021-01-01) */
    SU_PARAGRAF_13(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 3, 13),

    /** Kapittel 4 - § 17.Søknad (2021-01-01) */
    SU_PARAGRAF_17(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 4, 17),

    /** Kapittel 4 - § 18.Plikt til å gi opplysningar (2021-01-01) */
    SU_PARAGRAF_18(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 4, 18),

    /** Kapittel 4 - § 21.Kontroll (2021-01-01) */
    SU_PARAGRAF_21(Lov.LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE, 4, 21),
    ;
}

enum class Lov(val tittel: String) {
    LOV_OM_SUPPLERENDE_STØNAD_TIL_PERSONER_MED_KORT_BOTID_I_NORGE("Lov om supplerande stønad til personar med kort butid i Noreg"),
}
