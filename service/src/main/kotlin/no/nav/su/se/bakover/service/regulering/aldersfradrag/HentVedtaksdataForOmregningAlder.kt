package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock

internal class HentVedtaksdataForOmregningAlder(
    private val vedtakRepo: VedtakRepo,
    private val reguleringService: ReguleringServiceImpl,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun hent(
        saker: List<SakInfo>,
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeOmregnetAlder, SakTilRegulering>> {
        return saker.map { sakInfo ->
            Either.catch {
                hentVedtaksdataOgVurderOmSkalRegulere(fraOgMedMåned, sakInfo)
            }.getOrElse { feil ->
                BleIkkeOmregnetAlder.FeilunderVurderingAvVedtakstilstand(feil, sakInfo.saksnummer).left()
            }
        }
    }

    private fun hentVedtaksdataOgVurderOmSkalRegulere(
        fraOgMedMåned: Måned,
        sakInfo: SakInfo,
    ): Either<BleIkkeOmregnetAlder, SakTilRegulering> {
        val reguleringer = reguleringService.hentReguleringerForSak(sakInfo.sakId)
        reguleringer.filterIsInstance<ReguleringUnderBehandling>().let { r ->
            when (r.size) {
                0 -> {}
                1 -> return BleIkkeOmregnetAlder.TrengerIkkeOmregne.FinnesÅpenOmregning(sakInfo.saksnummer).left()
                else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer ${sakInfo.saksnummer}. Underliggende grunn: Det finnes fler enn en åpen regulering.")
            }
        }

        val vedtakSomKanRevurderes =
            vedtakRepo.hentVedtakSomKanRevurderesForSakFraOgMed(
                sakId = sakInfo.sakId,
                fraOgMed = fraOgMedMåned,
            )
        return hentGjeldendeVedtaksdataForRegulering(
            vedtakSomKanRevurderes = vedtakSomKanRevurderes,
            fraOgMedMåned = fraOgMedMåned,
            clock = clock,
            sakInfo = sakInfo,
        ).fold(
            ifLeft = {
                BleIkkeOmregnetAlder.TrengerIkkeOmregne.IkkeLøpendeSak(
                    saksnummer = sakInfo.saksnummer,
                ).left()
            },
            ifRight = { gjeldendeVedtaksdata ->
                log.info(
                    "Omregning: saksnummer=${sakInfo.saksnummer}," +
                        "fradrag=${gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.map { it.fradragstype }}",
                )
                val harAlderspensjonsfradrag =
                    gjeldendeVedtaksdata
                        .grunnlagsdata
                        .fradragsgrunnlag
                        .any {
                            it.fradragstype == Fradragstype.Alderspensjon && it.utenlandskInntekt == null
                        }
                if (!harAlderspensjonsfradrag) {
                    return BleIkkeOmregnetAlder.TrengerIkkeOmregne.HarIkkeAlderspensjonFradrag(sakInfo.saksnummer)
                        .left()
                }

                if (gjeldendeVedtaksdata.harStans()) {
                    return BleIkkeOmregnetAlder.TrengerIkkeOmregne.HarStans(sakInfo.saksnummer)
                        .left()
                }

                return SakTilRegulering(sakInfo = sakInfo, gjeldendeVedtaksdata = gjeldendeVedtaksdata).right()
            },
        )
    }
}
