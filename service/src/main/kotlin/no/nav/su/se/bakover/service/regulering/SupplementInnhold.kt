package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.person.Fnr
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

data class SupplementInnhold(
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

data class Supplement(
    val supplement: List<SupplementInnhold>,
) : List<SupplementInnhold> by supplement
