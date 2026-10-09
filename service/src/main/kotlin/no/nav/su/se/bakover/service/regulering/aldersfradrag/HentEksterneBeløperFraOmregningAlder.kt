package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.AlderspensjonFraPesys
import no.nav.su.se.bakover.domain.regulering.AlderspensjonOppslagsgrunnlag
import no.nav.su.se.bakover.domain.regulering.AlderspensjonOppslagsperson
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal class HentEksterneBeløperFraOmregningAlder(
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val satsFactory: SatsFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun hent(
        saker: List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>,
        fraOgMedMåned: Måned,
    ): Pair<List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>, List<AlderspensjonFraPesys>> {
        val sakerSomKanOmregnes = saker.filterRights()

        if (sakerSomKanOmregnes.isEmpty()) {
            return saker to emptyList()
        }

        val fradragstyper = listOf(Fradragstype.Alderspensjon)
        val grunnlagPerSak = saker.map { resultat ->
            resultat.flatMap { sak ->
                val grunnlagsdata = sak.gjeldendeVedtaksdata.grunnlagsdata
                val fradragEtterOmregningsmåned = grunnlagsdata.fradragsgrunnlag.filter {
                    fradragstyper.contains(it.fradragstype) &&
                        it.utenlandskInntekt == null &&
                        it.periode.fraOgMed.isAfter(fraOgMedMåned.fraOgMed)
                }
                if (fradragEtterOmregningsmåned.isNotEmpty()) {
                    return@flatMap BleIkkeOmregnetAlder.AlderspensjonsfradragStarterEtterOmregningsmåned(
                        omregningsmåned = fraOgMedMåned,
                        fradrag = fradragEtterOmregningsmåned.map {
                            BleIkkeOmregnetAlder.AlderspensjonsfradragStarterEtterOmregningsmåned.FradragEtterOmregningsmåned(
                                tilhører = it.tilhører,
                                periode = it.periode,
                            )
                        },
                        saksnummer = sak.sakInfo.saksnummer,
                    ).left()
                }
                val harFradragBruker = grunnlagsdata.hentBrukteFradragstyperBasertPåKunNorske(
                    fradragstyper,
                    fraOgMedMåned,
                    FradragTilhører.BRUKER,
                ).isNotEmpty()
                val harFradragEps = grunnlagsdata.hentBrukteFradragstyperBasertPåKunNorske(
                    fradragstyper,
                    fraOgMedMåned,
                    FradragTilhører.EPS,
                ).isNotEmpty()
                val eps = if (harFradragEps) grunnlagsdata.epsForMåned()[fraOgMedMåned] else null
                if (harFradragEps && eps == null) {
                    BleIkkeOmregnetAlder.ManglerEpsForAlderspensjonsfradrag(
                        sakId = sak.sakInfo.sakId,
                        måned = fraOgMedMåned,
                        saksnummer = sak.sakInfo.saksnummer,
                    ).left()
                } else {
                    AlderspensjonOppslagsgrunnlag(
                        brukerFnr = sak.sakInfo.fnr,
                        personer = listOfNotNull(
                            sak.sakInfo.fnr.takeIf { harFradragBruker }?.let {
                                AlderspensjonOppslagsperson(it, FradragTilhører.BRUKER)
                            },
                            eps?.let { AlderspensjonOppslagsperson(it, FradragTilhører.EPS) },
                        ),
                    ).right()
                }
            }
        }

        val fraPesys = reguleringerFraPesysService.hentReguleringerForOmregningAlder(
            måned = fraOgMedMåned,
            oppslagsgrunnlag = grunnlagPerSak.filterRights(),
            satsFactory = satsFactory,
        )

        val feilPåEksterneReguleringer = fraPesys.filterLefts()
        val eksterntRegulerteBeløp = fraPesys.filterRights()
        val feilPåOppslagsgrunnlag = grunnlagPerSak.filterLefts().associateBy { it.saksnummer }

        val sakerEtterHenting = saker.map { sakResultat ->
            sakResultat.flatMap { sakTilRegulering ->
                val grunnlagsfeil = feilPåOppslagsgrunnlag[sakTilRegulering.sakInfo.saksnummer]
                val feil = feilPåEksterneReguleringer.find {
                    it.fnr == sakTilRegulering.sakInfo.fnr
                }
                if (grunnlagsfeil != null) {
                    grunnlagsfeil.left()
                } else if (feil != null) {
                    listOf(
                        FradragTilhører.BRUKER to feil.feilBruker,
                        FradragTilhører.EPS to feil.feilEps,
                    ).filter { (_, feilForPerson) -> feilForPerson.isNotEmpty() }
                        .forEach { (tilhører, feilForPerson) ->
                            log.warn(
                                "Omregning: PESYS-oppslag feilet. sakId={}, tilhører={}, måned={}, feilkoder={}",
                                sakTilRegulering.sakInfo.sakId,
                                tilhører,
                                fraOgMedMåned,
                                feilForPerson.map { it.feilkode },
                            )
                        }
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
