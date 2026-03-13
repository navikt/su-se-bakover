package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.LocalDate

interface ReguleringHentEksterneReguleringerService {
    fun hentEksterneReguleringer(request: HentEksterneReguleringerRequest): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteFradragEksternKilde>>
}

/**
 * Objekt for å hente regulerte beløper som skal brukes som fradrag.
 * Basert på reguleringsmåned og en liste saker utledes alle brukere og eps'er som har fradrag som kan hentes eksternt.
 * Bruker vil alltid ha minst en fradragstype som kan hentes eksternt (inntekt etter uføre eller alderspensjon).
 * Eps vil kunne ha 0 relevante fradragstyper. Da vil liste med fradrag være tom.
 */
data class HentEksterneReguleringerRequest(
    val månedFørRegulering: LocalDate,
    val brukereMedEps: List<BrukerMedEps>,
) {

    data class BrukerMedEps(
        val bruker: PersonMedFradrag,
        val eps: PersonMedFradrag?,
        val sakstype: Sakstype,
    )

    data class PersonMedFradrag(
        val fnr: Fnr,
        val fradrag: List<Fradrag>,
    )

    companion object {
        private val relevanteFradragsTyper = listOf(
            Fradragstype.Alderspensjon,
            Fradragstype.Uføretrygd,
            // Fradragstype.Arbeidsavklaringspenger, TODO ??

            // OBS! Ligger ikke i fradragsgrunnlag men må utledes fra uførevilkår
            Fradragstype.ForventetInntekt,
        )

        fun toRequest(
            reguleringsMåned: Måned,
            forSaker: List<Sak>,
            clock: Clock,
        ): HentEksterneReguleringerRequest {
            return HentEksterneReguleringerRequest(
                månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1),
                brukereMedEps = forSaker.map { it.toBrukerMedEps(reguleringsMåned, clock) },
            )
        }

        private fun Sak.toBrukerMedEps(
            reguleringsMåned: Måned,
            clock: Clock,
        ): BrukerMedEps {
            val grunnlagsdata = hentGjeldendeVedtaksdata(reguleringsMåned, clock).getOrElse {
                throw IllegalStateException("Kan ikke hente eksterne fradrag for sak som ikke er løpende")
            }.grunnlagsdata

            return BrukerMedEps(
                sakstype = type,
                bruker = PersonMedFradrag(
                    fnr = fnr,
                    fradrag = grunnlagsdata.hentFradragBasertPå(
                        fradragstyper = relevanteFradragsTyper,
                        måned = reguleringsMåned,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                eps = grunnlagsdata.epsForMåned()[reguleringsMåned]?.let {
                    PersonMedFradrag(
                        fnr = it,
                        fradrag = grunnlagsdata.hentFradragBasertPå(
                            fradragstyper = relevanteFradragsTyper,
                            måned = reguleringsMåned,
                            tilhører = FradragTilhører.EPS,
                        ),
                    )
                },
            )
        }
    }
}

data class HentingAvRegulerteFradragFeiletForBruker(
    val fnr: Fnr,
    val alleFeil: List<FeilMedRegulertFradrag>,
)

interface FeilMedRegulertFradrag {
    object IngenPeriodeFraPesys : FeilMedRegulertFradrag
    object ManglerPeriodeFørOgEtterReguleringFraPesys : FeilMedRegulertFradrag
    object GrunnbeløpFraPesysUliktForventetGammelt : FeilMedRegulertFradrag
    object GrunnbeløpFraPesysUliktForventetNytt : FeilMedRegulertFradrag
    object OverlappendePeriodeFraPesys : FeilMedRegulertFradrag
}

class UthentingAvPerioderUføreFeilet : IllegalStateException()
class UthentingAvPerioderAlderFeilet : IllegalStateException()
