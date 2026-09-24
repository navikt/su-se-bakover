package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import satser.domain.SatsFactory

internal class HentEksterneBeløperFraOmregningAlder(
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val satsFactory: SatsFactory,
) {
    fun hent(
        saker: List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>,
        fraOgMedMåned: Måned,
    ): Pair<List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>, List<EksterntRegulerteBeløp>> {
        val sakerSomKanOmregnes = saker.filterRights()

        if (sakerSomKanOmregnes.isEmpty()) {
            return saker to emptyList()
        }

        val eksterntOppslagsgrunnlag = HentReguleringerPesysParameter.utledGrunnlagFraSaker(
            reguleringsMåned = fraOgMedMåned.fraOgMed.toMåned(),
            forSaker = sakerSomKanOmregnes,
        )

        val fraPesys = reguleringerFraPesysService.hentReguleringerForOmregningAlder(
            parameter = eksterntOppslagsgrunnlag,
            satsFactory = satsFactory,
        )

        val feilPåEksterneReguleringer = fraPesys.filterLefts()
        val eksterntRegulerteBeløp = fraPesys.filterRights()

        val sakerEtterHenting = saker.map {
            it.flatMap { sakTilRegulering ->
                val feil = feilPåEksterneReguleringer.find {
                    it.fnr == sakTilRegulering.sakInfo.fnr
                }
                if (feil != null) {
                    BleIkkeOmregnetAlder.UthentingFradragEksterntFeilet(
                        feil,
                        sakTilRegulering.sakInfo.saksnummer,
                    ).left()
                } else {
                    sakTilRegulering.right()
                }
            }
        }
        return sakerEtterHenting to eksterntRegulerteBeløp
    }
}
