package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock

internal class HentVedtaksdataForOmregningAlder(
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
) {
    fun hent(
        saker: List<SakInfo>,
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeOmregnetAlder, SakTilRegulering>> {
        return saker.map { sakInfo ->
            // TODO(): Sjekk om det allerede finnes en åpen omregning for saken før ny omregning opprettes
            val vedtakSomKanRevurderes =
                vedtakRepo.hentVedtakSomKanRevurderesForSakFraOgMed(
                    sakId = sakInfo.sakId,
                    fraOgMed = fraOgMedMåned,
                )
            hentGjeldendeVedtaksdataForRegulering(
                vedtakSomKanRevurderes = vedtakSomKanRevurderes,
                fraOgMedMåned = fraOgMedMåned,
                clock = clock,
                sakInfo = sakInfo,
            ).fold(
                ifLeft = { bleIkkeRegulert ->
                    BleIkkeOmregnetAlder.FraReguleringsflyt(
                        bleIkkeRegulert,
                    ).left()
                },
                ifRight = { gjeldendeVedtaksdata ->
                    val harAlderspensjonsfradrag =
                        gjeldendeVedtaksdata
                            .grunnlagsdata
                            .fradragsgrunnlag
                            .any {
                                it.fradragstype == Fradragstype.Alderspensjon
                            }
                    if (harAlderspensjonsfradrag) {
                        SakTilRegulering(
                            sakInfo = sakInfo,
                            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                        ).right()
                    } else {
                        BleIkkeOmregnetAlder.ManglerAlderspensjonsfradrag(
                            saksnummer = sakInfo.saksnummer,
                        ).left()
                    }
                },
            )
        }
    }
}
