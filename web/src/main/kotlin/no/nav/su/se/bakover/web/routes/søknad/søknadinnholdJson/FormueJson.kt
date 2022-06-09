package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.Formue
import no.nav.su.se.bakover.domain.Kjøretøy
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.KjøretøyJson.Companion.toKjøretøyJson

data class FormueJson(
    val eierBolig: Boolean,
    val borIBolig: Boolean? = null,
    val verdiPåBolig: Number? = null,
    val boligBrukesTil: String? = null,
    val depositumsBeløp: Number? = null,
    val verdiPåEiendom: Number? = null,
    val eiendomBrukesTil: String? = null,
    val kjøretøy: List<KjøretøyJson>? = null,
    val innskuddsBeløp: Number? = null,
    val verdipapirBeløp: Number? = null,
    val skylderNoenMegPengerBeløp: Number? = null,
    val kontanterBeløp: Number? = null
) {
    fun toFormue() = Formue(
        eierBolig = eierBolig,
        borIBolig = borIBolig,
        verdiPåBolig = verdiPåBolig,
        boligBrukesTil = boligBrukesTil,
        depositumsBeløp = depositumsBeløp,
        verdiPåEiendom = verdiPåEiendom,
        eiendomBrukesTil = eiendomBrukesTil,
        kjøretøy = kjøretøy.toKjøretøyList(),
        innskuddsBeløp = innskuddsBeløp,
        verdipapirBeløp = verdipapirBeløp,
        skylderNoenMegPengerBeløp = skylderNoenMegPengerBeløp,
        kontanterBeløp = kontanterBeløp
    )

    private fun List<KjøretøyJson>?.toKjøretøyList() = this?.map {
        it.toKjøretøy()
    }

    companion object {
        fun Formue.toFormueJson() =
            FormueJson(
                eierBolig = eierBolig,
                borIBolig = borIBolig,
                verdiPåBolig = verdiPåBolig,
                boligBrukesTil = boligBrukesTil,
                depositumsBeløp = depositumsBeløp,
                verdiPåEiendom = verdiPåEiendom,
                eiendomBrukesTil = eiendomBrukesTil,
                kjøretøy = kjøretøy.toKjøretøyListJson(),
                innskuddsBeløp = innskuddsBeløp,
                verdipapirBeløp = verdipapirBeløp,
                skylderNoenMegPengerBeløp = skylderNoenMegPengerBeløp,
                kontanterBeløp = kontanterBeløp
            )

        fun List<Kjøretøy>?.toKjøretøyListJson() = this?.map {
            it.toKjøretøyJson()
        }
    }
}

data class KjøretøyJson(val verdiPåKjøretøy: Number, val kjøretøyDeEier: String) {
    fun toKjøretøy() = Kjøretøy(
        kjøretøyDeEier = kjøretøyDeEier,
        verdiPåKjøretøy = verdiPåKjøretøy
    )

    companion object {
        fun Kjøretøy.toKjøretøyJson() =
            KjøretøyJson(
                kjøretøyDeEier = kjøretøyDeEier,
                verdiPåKjøretøy = verdiPåKjøretøy
            )
    }
}
