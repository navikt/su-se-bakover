package no.nav.su.se.bakover.domain.regulering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.inneholder
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

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

    fun getForType(fradragstype: Fradragstype) = perType.find { it.type == fradragstype }
    val fradagstyper = perType.map { it.type }
    // TODO - må muligens ha en smartere periodehåndtering
    // val perioder: NonEmptyList<Periode> = perType.flatMap { it.perioder }

    /**
     * Innenfor en person, har vi et objekt per fradragstype, men vi støtter flere ikke-overlappende perioder, dvs. hull mellom periodene.
     * Dersom vi senere må ta høyde for overlapp av perioder, i forbindelse med overskrivende vedtak, trenger vi en diskriminator og tidslinjelogikk.
     */
    data class PerType(
        val fradragsperioder: NonEmptyList<Fradragsperiode>,
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
            // TODO - må muligens ha en smartere periodehåndtering
            // require(!fradragsperioder.map { it.periode }.harOverlappende())
        }

        // TODO - må muligens ha en smartere periodehåndtering
        // val perioder: NonEmptyList<Periode> = fradragsperioder.map { it.periode }

        // TODO tester
        fun inneholder(periode: Periode): Boolean = fradragsperioder.any { it.inneholder(periode) }

        // TODO tester
        fun inneholder(periode: List<Periode>): Boolean =
            fradragsperioder.any { p1 -> periode.all { p1.inneholder(it) } }

        data class Fradragsperiode(
            val fraOgMed: LocalDate,
            val tilOgMed: LocalDate?,
            val beløp: Int,
            val vedtakstype: Vedtakstype,
            val eksterndata: Eksterndata,
        ) {

            init {
                require(fraOgMed.erFørsteDagIMåned())
                tilOgMed?.let {
                    // Bruker periode sin validering.
                    Periode.create(fraOgMed, it)
                }
            }

            // TODO tester
            fun periode(): Periode? = tilOgMed?.let { Periode.create(fraOgMed, it) }

            // TODO tester
            fun inneholder(other: Periode): Boolean {
                return this.periode()?.inneholder(other) ?: (other.tilOgMed >= this.fraOgMed)
            }

            // TODO - lagres direkte i basen
            enum class Vedtakstype {
                Endring,
                Regulering,
            }

            /**
             * De eksterne dataene unparsed - for persistering og visning.
             */
            data class Eksterndata(
                /** FNR: Eksempel: 12345678901 */
                val fnr: String,
                /** K_SAK_T: Eksempel: UFOREP/GJENLEV/ALDER */
                val sakstype: String,
                /** K_VEDTAK_T: Eksempel: ENDRING/REGULERING */
                val vedtakstype: String,
                /** FOM_DATO: Eksempel: 01.04.2024 */
                val fraOgMed: String,
                /** TOM_DATO: Eksempel: 30.04.2024 */
                val tilOgMed: String?,
                /**
                 * Summen av alle brutto_yk per vedtak
                 * BRUTTO: Eksempel: 27262
                 */
                val bruttoYtelse: String,
                /**
                 * Summen av alle netto_yk per vedtak
                 * NETTO: Eksempel: 28261
                 */
                val nettoYtelse: String,
                /** K_YTELSE_KOMP_T: Eksempel: UT_ORDINER, UT_GJT, UT_TSB, ... */
                val ytelseskomponenttype: String,
                /** BRUTTO_YK: Eksempel: 2199 */
                val bruttoYtelseskomponent: String,
                /** NETTO_YK: Eksempel: 26062 */
                val nettoYtelseskomponent: String,
            )
        }
    }

    fun inneholderFradragForTypeOgMåned(
        type: Fradragstype,
        måned: Måned,
    ): Boolean {
        // TODO: Det kan hende dataen vi får bare er periodisert fra mai og ut året, det må vi i så fall ta høyde for.
        return hentForType(type)?.inneholder(måned) ?: false
    }

    fun inneholderFradragForTypeOgPeriode(
        type: Fradragstype,
        periode: Periode,
    ): Boolean {
        // TODO: Det kan hende dataen vi får bare er periodisert fra mai og ut året, det må vi i så fall ta høyde for.
        return hentForType(type)?.inneholder(periode) ?: false
    }

    fun inneholderFradragForTypeOgPerioder(
        type: Fradragstype,
        perioder: List<Periode>,
    ): Boolean {
        return hentForType(type)?.inneholder(perioder) ?: false
    }

    fun hentForType(type: Fradragstype): PerType? = perType.singleOrNull { it.type == type }
}
