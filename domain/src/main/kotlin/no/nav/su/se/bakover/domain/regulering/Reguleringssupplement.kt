package no.nav.su.se.bakover.domain.regulering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.inneholder
import vilkår.inntekt.domain.grunnlag.Fradragstype

/**
 * Et reguleringssupplement er data som mottas fra en ekstern kilde, for eksempel PESYS, som brukes for å justere
 * på utbetalingen.
 */
data class Reguleringssupplement(
    private val supplement: List<ReguleringssupplementFor>,
) : List<ReguleringssupplementFor> by supplement {

    fun getFor(fnr: Fnr): ReguleringssupplementFor? = this.supplement.singleOrNull { it.fnr == fnr }

    companion object {
        fun empty() = Reguleringssupplement(emptyList())
    }
}

data class ReguleringssupplementFor(
    val fnr: Fnr,
    val innhold: List<ReguleringssupplementInnhold>,
) {
    init {
        require(innhold.all { it.fnr == fnr })
    }

    val fradagstyper = innhold.map { it.type }

    fun inneholderFradragForTypeOgMåned(
        type: Fradragstype,
        måned: Måned,
    ): Boolean {
        // TODO: Det kan hende dataen vi får bare er periodisert fra mai og ut året, det må vi i så fall ta høyde for.
        return hentForType(type)?.perioder?.inneholder(måned) ?: false
    }

    fun inneholderFradragForTypeOgPeriode(
        type: Fradragstype,
        periode: Periode,
    ): Boolean {
        // TODO: Det kan hende dataen vi får bare er periodisert fra mai og ut året, det må vi i så fall ta høyde for.
        return hentForType(type)?.perioder?.inneholder(periode) ?: false
    }

    fun inneholderFradragForTypeOgPerioder(
        type: Fradragstype,
        perioder: List<Periode>,
    ): Boolean {
        return hentForType(type)?.perioder?.inneholder(perioder) ?: false
    }

    fun hentForType(type: Fradragstype): ReguleringssupplementInnhold? {
        return innhold.singleOrNull { it.type == type }
    }
}

/**
 * Formatet som vi forventer å motta reguleringssupplementet i.
 */
data class ReguleringssupplementInnhold(
    val fnr: Fnr,
    val perType: NonEmptyList<PerType>,
) {
    val type = perType.first().type

    val perioder: NonEmptyList<Periode> = perType.flatMap { it.fradragsperiode.map { it.periode } }

    init {
        perType.map { it.type }.let {
            require(it.distinct() == it)
        }
    }

    data class PerType(
        val fradragsperiode: NonEmptyList<Fradragsperiode>,
        val type: Fradragstype,
    ) {
        init {
            // TODO jah: Vi antar at vi ikke kan få overlappende perioder innenfor et fnr+type
            //  Hvis denne antagelsen ikke stemmer, må vi lage en tidslinje basert på et tidspunkt eller rekkefølge de har.
            require(!fradragsperiode.map { it.periode }.harOverlappende())
        }
    }

    data class Fradragsperiode(
        val periode: Periode,
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
}
