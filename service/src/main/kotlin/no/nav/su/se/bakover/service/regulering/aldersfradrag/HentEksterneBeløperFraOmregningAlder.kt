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

// Henter eksterne alderspensjonsbeløp fra PESYS for omregning
internal class HentEksterneBeløperFraOmregningAlder(
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val satsFactory: SatsFactory,
) {
    // henter alderspensjonsbeløp fra PESYS og returnerer saker som kan gå videre i omregningen
    fun hent(
        saker: List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>,
        fraOgMedMåned: Måned,
    ): Pair<List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>, List<EksterntRegulerteBeløp>> {
        // henter bare saker som fortsatt kan gå videre i omregningen
        val sakerSomKanOmregnes = saker.filterRights()

        if (sakerSomKanOmregnes.isEmpty()) {
            return saker to emptyList()
        }

        // Bygger grunnlaget som brukes for å hente alderspensjon fra PESYS
        val eksterntOppslagsgrunnlag = HentReguleringerPesysParameter.utledGrunnlagFraSaker(
            reguleringsMåned = fraOgMedMåned.fraOgMed.toMåned(),
            forSaker = sakerSomKanOmregnes,
        )

        // Henter kun alderspensjonsbeløp fra PESYS
        val fraPesys = reguleringerFraPesysService.hentReguleringerForOmregningAlder(
            parameter = eksterntOppslagsgrunnlag,
            satsFactory = satsFactory,
        )

        // Henter ut saker der oppslaget mot PESYS feilet
        val feilPåEksterneReguleringer = fraPesys.filterLefts()

        // Henter ut alderspensjonsbeløpene som ble funnet i PESYS.
        val eksterntRegulerteBeløp = fraPesys.filterRights()

        // Kobler eventuelle PESYS-feil til riktig sak.
        val sakerEtterHenting = saker.map {
            it.flatMap { sakTilRegulering ->
                val feil = feilPåEksterneReguleringer.find {
                    it.fnr == sakTilRegulering.sakInfo.fnr
                }
                if (feil != null) {
                    BleIkkeOmregnetAlder.OmregningFeiletVedKlargjøring.UthentingFradragEksterntFeilet(
                        feil,
                        sakTilRegulering.sakInfo.saksnummer,
                    ).left()
                } else {
                    sakTilRegulering.right()
                }
            }
        }
        // Returnerer sakene etter PESYS-henting sammen med alderspensjonsbeløpene.
        return sakerEtterHenting to eksterntRegulerteBeløp
    }
}
