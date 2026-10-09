package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import org.slf4j.LoggerFactory
import vedtak.domain.VedtakSomKanRevurderes
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
        val sjekkedeSaker = saker.map { sakInfo ->
            Either.catch { harÅpenRegulering(sakInfo) }
                .mapLeft { feil ->
                    BleIkkeOmregnetAlder.FeilunderVurderingAvVedtakstilstand(
                        feil,
                        sakInfo.saksnummer,
                    )
                }
                .flatMap { resultat -> resultat.map { sakInfo } }
        }

        val sakerSomIkkeSkalVidere = sjekkedeSaker.filterLefts()
        val sakerSomSkalVidere = sjekkedeSaker.filterRights()
        val vedtakPerSak = Either.catch {
            vedtakRepo.hentVedtakSomKanRevurderesForSakerFraOgMed(
                sakIder = sakerSomSkalVidere.map { it.sakId },
                fraOgMed = fraOgMedMåned,
            )
        }

        val vurderteSaker = vedtakPerSak.fold(
            ifLeft = { feil ->
                sakerSomSkalVidere.map { sakInfo ->
                    BleIkkeOmregnetAlder.FeilunderVurderingAvVedtakstilstand(
                        feil,
                        sakInfo.saksnummer,
                    ).left()
                }
            },
            ifRight = { vedtak ->
                sakerSomSkalVidere.map { sakInfo ->
                    vurderSak(
                        fraOgMedMåned = fraOgMedMåned,
                        sakInfo = sakInfo,
                        vedtakSomKanRevurderes = vedtak[sakInfo.sakId].orEmpty(),
                    )
                }
            },
        )

        return sakerSomIkkeSkalVidere.map { it.left() } + vurderteSaker
    }

    private fun vurderSak(
        fraOgMedMåned: Måned,
        sakInfo: SakInfo,
        vedtakSomKanRevurderes: List<VedtakSomKanRevurderes>,
    ): Either<BleIkkeOmregnetAlder, SakTilRegulering> =
        Either.catch {
            hentVedtaksdataOgVurderOmSkalRegulere(
                fraOgMedMåned = fraOgMedMåned,
                sakInfo = sakInfo,
                vedtakSomKanRevurderes = vedtakSomKanRevurderes,
            )
        }.mapLeft { feil ->
            BleIkkeOmregnetAlder.FeilunderVurderingAvVedtakstilstand(
                feil,
                sakInfo.saksnummer,
            )
        }.flatMap { it }

    private fun harÅpenRegulering(sakInfo: SakInfo): Either<BleIkkeOmregnetAlder, Unit> {
        val reguleringer = reguleringService.hentReguleringerForSak(sakInfo.sakId)
            .filterIsInstance<ReguleringUnderBehandling>()
        return when (reguleringer.size) {
            0 -> Unit.right()
            1 -> BleIkkeOmregnetAlder.TrengerIkkeOmregne.FinnesÅpenOmregning(sakInfo.saksnummer).left()
            else -> throw IllegalStateException(
                "Kunne ikke opprette eller oppdatere regulering for saksnummer ${sakInfo.saksnummer}. " +
                    "Underliggende grunn: Det finnes fler enn en åpen regulering.",
            )
        }
    }

    private fun hentVedtaksdataOgVurderOmSkalRegulere(
        fraOgMedMåned: Måned,
        sakInfo: SakInfo,
        vedtakSomKanRevurderes: List<VedtakSomKanRevurderes>,
    ): Either<BleIkkeOmregnetAlder, SakTilRegulering> {
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

                if (gjeldendeVedtaksdata.periode.fraOgMed.isAfter(fraOgMedMåned.fraOgMed)) {
                    return BleIkkeOmregnetAlder.VedtakStarterEtterOmregningsmåned(
                        omregningsmåned = fraOgMedMåned,
                        vedtaksperiode = gjeldendeVedtaksdata.periode,
                        saksnummer = sakInfo.saksnummer,
                    ).left()
                }
                log.info(
                    "Omregning: saksnummer=${sakInfo.saksnummer}," +
                        "fradrag=${gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.map { it.fradragstype }}",
                )

                return SakTilRegulering(sakInfo = sakInfo, gjeldendeVedtaksdata = gjeldendeVedtaksdata).right()
            },
        )
    }
}
