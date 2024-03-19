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
 * på utbetalingen. Denne vil være delt opp i en [ReguleringssupplementFor] per person (kan være både brukere og EPS).
 */
data class Reguleringssupplement(
    private val supplement: List<ReguleringssupplementFor>,
) : List<ReguleringssupplementFor> by supplement {

    fun getFor(fnr: Fnr): ReguleringssupplementFor? = this.supplement.singleOrNull { it.fnr == fnr }

    companion object {
        fun empty() = Reguleringssupplement(emptyList())
    }
}

/**
 * Lager et objekt per person (kan være både brukere og EPS), uavhengig av hvor mange kilder vi bruker.
 */
data class ReguleringssupplementFor(
    val fnr: Fnr,
    val perType: NonEmptyList<PerType>,
) {
    init {
        perType.map { it.type }.let {
            require(it.distinct() == it)
        }
    }

    val fradagstyper = perType.map { it.type }
    val perioder: NonEmptyList<Periode> = perType.flatMap { it.perioder }

    /**
     * Innenfor en person, har vi et objekt per fradragstype, men vi støtter flere ikke-overlappende perioder, dvs. hull mellom periodene.
     * Dersom vi senere må ta høyde for overlapp av perioder, i forbindelse med overskrivende vedtak, trenger vi en diskriminator og tidslinjelogikk.
     */
    data class PerType(
        val fradragsperiode: NonEmptyList<Fradragsperiode>,
        /**
         * TODO - per i dag, så henter vi bare fradragene som er i Pesys. Disse er bare et subset av Fradragstypene
         * - Alderspensjon
         * - AvtalefestetPensjon
         * - AvtalefestetPensjonPrivat
         * - Gjenlevendepensjon
         * - Uføretrygd
         */
        val type: Fradragstype,
    ) {
        init {
            // TODO jah: Vi antar at vi ikke kan få overlappende perioder innenfor et fnr+type
            //  Hvis denne antagelsen ikke stemmer, må vi lage en tidslinje basert på et tidspunkt eller rekkefølge de har.
            require(!fradragsperiode.map { it.periode }.harOverlappende())
        }

        val perioder: NonEmptyList<Periode> = fradragsperiode.map { it.periode }

        data class Fradragsperiode(
            val periode: Periode,
            val beløp: Int,
        )
    }

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

    fun hentForType(type: Fradragstype): PerType? = perType.singleOrNull { it.type == type }
}
