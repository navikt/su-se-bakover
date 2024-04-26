package no.nav.su.se.bakover.domain.regulering.supplement

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

/**
 * Lager et objekt per person (kan være både brukere og EPS), uavhengig av hvor mange kilder vi bruker.
 */
data class ReguleringssupplementFor(
    val fnr: Fnr,
    val perType: NonEmptyList<PerType>,
) {
    init {
        perType.map { it.kategori }.let {
            require(it.distinct() == it)
        }
    }

    fun getForType(fradragstype: Fradragstype) = perType.find { it.kategori == fradragstype.kategori }

    // TODO - test
    fun eksternedataForAlleTyper(): NonEmptyList<PerType.Fradragsperiode.Eksterndata> =
        perType.flatMap { it.vedtak.flatMap { it.eksterneData() } }

    /**
     * Innenfor en person, har vi et objekt per fradragstype, men vi støtter flere ikke-overlappende perioder, dvs. hull mellom periodene.
     * Dersom vi senere må ta høyde for overlapp av perioder, i forbindelse med overskrivende vedtak, trenger vi en diskriminator og tidslinjelogikk.
     */
    data class PerType(
        val vedtak: NonEmptyList<Eksternvedtak>,
        /**
         * TODO - per i dag, så henter vi bare fradragene som er i Pesys. Disse er bare et subset av Fradragstypene
         * - Alderspensjon
         * - AvtalefestetPensjon
         * - AvtalefestetPensjonPrivat
         * - Gjenlevendepensjon
         * - Uføretrygd
         */
        val kategori: Fradragstype.Kategori,
    ) {
        val endringsvedtak: Eksternvedtak.Endring = vedtak.filterIsInstance<Eksternvedtak.Endring>().single()
        val reguleringsvedtak: List<Eksternvedtak.Regulering> = vedtak.filterIsInstance<Eksternvedtak.Regulering>()

        init {
            require(!vedtak.overlapper()) {
                "Vedtakene til Pesys kan ikke overlappe, men var ${vedtak.map { Pair(it.fraOgMed, it.tilOgMed) }}"
            }
        }

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
}
