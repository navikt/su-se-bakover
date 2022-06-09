package no.nav.su.se.bakover.domain.søknadinnhold

import arrow.core.Either
import arrow.core.left
import arrow.core.right

data class Formue(
    val eierBolig: Boolean,
    val borIBolig: Boolean? = null,
    val verdiPåBolig: Number? = null,
    val boligBrukesTil: String? = null,
    val depositumsBeløp: Number? = null,
    val verdiPåEiendom: Number? = null,
    val eiendomBrukesTil: String? = null,
    val kjøretøy: List<Kjøretøy>? = null,
    val innskuddsBeløp: Number? = null,
    val verdipapirBeløp: Number? = null,
    val skylderNoenMegPengerBeløp: Number? = null,
    val kontanterBeløp: Number? = null,
) {

    companion object {
        fun tryCreate(
            eierBolig: Boolean,
            borIBolig: Boolean? = null,
            verdiPåBolig: Number? = null,
            boligBrukesTil: String? = null,
            depositumsBeløp: Number? = null,
            verdiPåEiendom: Number? = null,
            eiendomBrukesTil: String? = null,
            kjøretøy: List<Kjøretøy>? = null,
            innskuddsBeløp: Number? = null,
            verdipapirBeløp: Number? = null,
            skylderNoenMegPengerBeløp: Number? = null,
            kontanterBeløp: Number? = null,
        ): Either<FeilVedOpprettelseAvFormue, Formue> {
            validerBorIBolig(eierBolig, borIBolig).mapLeft { return it.left() }
            validerDepositumsBeløp(eierBolig, depositumsBeløp).mapLeft { return it.left() }
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

        private fun validerDepositumsBeløp(
            eierBolig: Boolean,
            depositumsBeløp: Number?,
        ) =
            if (!eierBolig && depositumsBeløp == null) FeilVedOpprettelseAvFormue.DepositumsbeløpetErIkkeutfylt.left() else Unit.right()

        private fun validerBolig(
            eierBolig: Boolean,
            borIBolig: Boolean?,
            verdiPåBolig: Number?,
            boligBrukesTil: String?,
        ) =
            if ((eierBolig && borIBolig == false) && (verdiPåBolig == null || boligBrukesTil == null)) FeilVedOpprettelseAvFormue.BoligensVerdiEllerBeskrivelseErIkkeUtfylt.left() else Unit.right()
    }
}

data class Bolig(
    val verdi: Number,
    val brukesTil: String,
)

data class Eiendom(
    val verdi: Number,
    val brukesTil: String,
)

data class Kjøretøy(
    val verdiPåKjøretøy: Number,
    val kjøretøyDeEier: String,
)

sealed interface FeilVedOpprettelseAvFormue {
    object DepositumsbeløpetErIkkeutfylt : FeilVedOpprettelseAvFormue
    object BorIBoligErIkkeUtfylt : FeilVedOpprettelseAvFormue
    object BoligensVerdiEllerBeskrivelseErIkkeUtfylt : FeilVedOpprettelseAvFormue
}
