package no.nav.su.se.bakover.domain.søknad.søknadinnhold

import arrow.core.Either
import arrow.core.left
import arrow.core.right

data class Formue private constructor(
    val eierBolig: Boolean,
    val borIBolig: Boolean?,
    val verdiPåBolig: Number?,
    val boligBrukesTil: String?,
    val depositumsBeløp: Number?,
    val verdiPåEiendom: Number?,
    val eiendomBrukesTil: String?,
    val kjøretøy: List<Kjøretøy>?,
    val innskuddsBeløp: Number?,
    val verdipapirBeløp: Number?,
    val skylderNoenMegPengerBeløp: Number?,
    val kontanterBeløp: Number?,
) {

    companion object {
        fun tryCreate(
            eierBolig: Boolean,
            borIBolig: Boolean?,
            verdiPåBolig: Number?,
            boligBrukesTil: String?,
            depositumsBeløp: Number?,
            verdiPåEiendom: Number?,
            eiendomBrukesTil: String?,
            kjøretøy: List<Kjøretøy>?,
            innskuddsBeløp: Number?,
            verdipapirBeløp: Number?,
            skylderNoenMegPengerBeløp: Number?,
            kontanterBeløp: Number?,
        ): Either<FeilVedOpprettelseAvFormue, Formue> {
            validerBorIBolig(eierBolig, borIBolig).mapLeft { return it.left() }
            validerBolig(eierBolig, borIBolig, verdiPåBolig, boligBrukesTil).mapLeft { return it.left() }

            return Formue(
                eierBolig = eierBolig,
                borIBolig = borIBolig,
                verdiPåBolig = verdiPåBolig,
                boligBrukesTil = boligBrukesTil,
                depositumsBeløp = depositumsBeløp,
                verdiPåEiendom = verdiPåEiendom,
                eiendomBrukesTil = eiendomBrukesTil,
                kjøretøy = kjøretøy,
                innskuddsBeløp = innskuddsBeløp,
                verdipapirBeløp = verdipapirBeløp,
                skylderNoenMegPengerBeløp = skylderNoenMegPengerBeløp,
                kontanterBeløp = kontanterBeløp,
            ).right()
        }

        private fun validerBorIBolig(
            eierBolig: Boolean,
            borIBolig: Boolean?,
        ) =
            if (eierBolig && borIBolig == null) FeilVedOpprettelseAvFormue.BorIBoligErIkkeUtfylt.left() else Unit.right()

        private fun validerBolig(
            eierBolig: Boolean,
            borIBolig: Boolean?,
            verdiPåBolig: Number?,
            boligBrukesTil: String?,
        ) =
            if ((eierBolig && borIBolig == false) && (verdiPåBolig == null || boligBrukesTil == null)) FeilVedOpprettelseAvFormue.BoligensVerdiEllerBeskrivelseErIkkeUtfylt.left() else Unit.right()
    }
}

data class Kjøretøy(
    val verdiPåKjøretøy: Number,
    val kjøretøyDeEier: String,
)

sealed interface FeilVedOpprettelseAvFormue {
    object DepositumsbeløpetErIkkeutfylt : FeilVedOpprettelseAvFormue
    object BorIBoligErIkkeUtfylt : FeilVedOpprettelseAvFormue
    object BoligensVerdiEllerBeskrivelseErIkkeUtfylt : FeilVedOpprettelseAvFormue
}
