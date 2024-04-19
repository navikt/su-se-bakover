package no.nav.su.se.bakover.domain.regulering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import no.nav.su.se.bakover.common.domain.tid.erSisteDagIMåned
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
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
        val type: Fradragstype,
    ) {
        val endringsvedtak: Eksternvedtak.Endring = vedtak.filterIsInstance<Eksternvedtak.Endring>().single()
        val reguleringsvedtak: List<Eksternvedtak.Regulering> = vedtak.filterIsInstance<Eksternvedtak.Regulering>()

        init {
            require(!vedtak.overlapper()) {
                "Vedtakene til Pesys kan ikke overlappe, men var ${vedtak.map { Pair(it.fraOgMed, it.tilOgMed) }}"
            }
        }

        sealed interface Eksternvedtak {
            val fraOgMed: LocalDate
            val tilOgMed: LocalDate?
            val fradrag: NonEmptyList<Fradragsperiode>
            val beløp: Int

            fun overlapper(other: Eksternvedtak): Boolean {
                val thisEnd = tilOgMed ?: LocalDate.MAX
                val otherEnd = other.tilOgMed ?: LocalDate.MAX

                return this.fraOgMed <= otherEnd && other.fraOgMed <= thisEnd
            }

            fun overlapper(other: List<Eksternvedtak>): Boolean {
                return other.any { it.overlapper(this) }
            }

            data class Regulering(
                override val fraOgMed: LocalDate,
                override val tilOgMed: LocalDate?,
                override val fradrag: NonEmptyList<Fradragsperiode>,
                override val beløp: Int,
            ) : Eksternvedtak {
                init {
                    require(fradrag.all { it.fraOgMed == fraOgMed }) {
                        "Forventet tilOgMed $fraOgMed, men var ${fradrag.map { fraOgMed }}"
                    }
                    require(fradrag.all { it.tilOgMed == tilOgMed }) {
                        "Forventet tilOgMed $tilOgMed, men var ${fradrag.map { tilOgMed }}"
                    }
                    require(fradrag.all { it.beløp == beløp }) {
                        "Forventet beløp $beløp, men var ${fradrag.map { beløp }}"
                    }
                    require(fradrag.all { it.vedtakstype == Fradragsperiode.Vedtakstype.Regulering }) {
                        "Forventet at alle fradragene har vedtakstype ${Fradragsperiode.Vedtakstype.Regulering}, men var ${fradrag.map { it.vedtakstype }} "
                    }
                    require(fraOgMed.erFørsteDagIMåned()) {
                        "Forventer at vedtakene løper over hele måneder, men var: fraOgMed: $fraOgMed, tilOgMed: $tilOgMed"
                    }
                    tilOgMed?.let {
                        require(it.erSisteDagIMåned()) {
                            "Forventer at vedtakene løper over hele måneder, men var: fraOgMed: $fraOgMed, tilOgMed: $tilOgMed"
                        }
                    }
                }
            }

            /**
             * Vi ønsker kun 1 måned med endringsdato i de tilfellene Pesys og SU reguleres samtidig.
             * Denne måneden skal være før reguleringsperioden.
             */
            data class Endring(
                val måned: Måned,
                override val fradrag: NonEmptyList<Fradragsperiode>,
                override val beløp: Int,
            ) : Eksternvedtak {
                override val fraOgMed: LocalDate = måned.fraOgMed
                override val tilOgMed: LocalDate = måned.tilOgMed

                init {
                    require(fradrag.all { it.fraOgMed == fraOgMed })
                    require(fradrag.all { it.tilOgMed == tilOgMed })
                    require(fradrag.all { it.beløp == beløp })
                    require(fradrag.all { it.vedtakstype == Fradragsperiode.Vedtakstype.Endring })
                }
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
}

fun List<ReguleringssupplementFor.PerType.Eksternvedtak>.overlapper(): Boolean {
    return this.any { it.overlapper(this.minus(it)) }
}

fun List<ReguleringssupplementFor.PerType.Eksternvedtak>.overlapper(other: List<ReguleringssupplementFor.PerType.Eksternvedtak>): Boolean {
    return this.any { it.overlapper(other) }
}
