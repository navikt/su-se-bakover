package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

data class Reguleringssupplement(
    private val supplement: List<ReguleringssupplementInnhold>,
) : List<ReguleringssupplementInnhold> by supplement {

    companion object {
        fun empty() = Reguleringssupplement(emptyList())
        fun from(innhold: ReguleringssupplementInnhold) = Reguleringssupplement(listOf(innhold))
    }
}

data class ReguleringssupplementInnhold(
    val fnr: Fnr,
    val fom: LocalDate,
    val tom: LocalDate,
    /**
     * TODO - per i dag, så henter vi bare fradragene som er i Pesys. Disse er bare et subset av Fradragstypene
     * - Alderspensjon
     * - AvtalefestetPensjon
     * - AvtalefestetPensjonPrivat
     * - Gjenlevendepensjon
     * - Uføretrygd
     */
    val type: Fradragstype,
    val beløp: Int,
)
