package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Måned
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
        val antallÅpnePerSak = Either.catch {
            reguleringService.hentAntallÅpneReguleringerForSaker(saker.map { it.sakId })
        }
        val sjekkedeSaker = antallÅpnePerSak.fold(
            ifLeft = { feil ->
                // Kaster for alle i batchen
                saker.map { sakInfo ->
                    BleIkkeOmregnetAlder.FeilunderVurderingAvVedtakstilstand(
                        feil,
                        sakInfo.saksnummer,
                    ).left()
                }
            },
            ifRight = { antall ->
                saker.map { sakInfo ->
                    sjekkÅpneReguleringer(sakInfo, antall[sakInfo.sakId] ?: 0L)
                }
            },
        )

        val sakerSomIkkeSkalVidere = sjekkedeSaker.filterLefts()
        val sakerSomSkalVidere = sjekkedeSaker.filterRights()
        if (sakerSomSkalVidere.isEmpty()) return sakerSomIkkeSkalVidere.map { it.left() }
        val vedtakPerSak = Either.catch {
            vedtakRepo.hentVedtakSomKanRevurderesForSakerFraOgMed(
                sakIder = sakerSomSkalVidere.map { it.sakId },
                fraOgMed = fraOgMedMåned,
            )
        }

        val vurderteSaker = vedtakPerSak.fold(
            ifLeft = { feil ->
                // Kaster alle i batchen
                sakerSomSkalVidere.map { sakInfo ->
                    BleIkkeOmregnetAlder.FeilunderVurderingAvVedtakstilstand(
                        feil,
                        sakInfo.saksnummer,
                    ).left()
                }
            },
            ifRight = { vedtak ->
                sakerSomSkalVidere.map { sakInfo ->
                    Either.catch {
                        hentVedtaksdataOgVurderOmSkalRegulere(
                            fraOgMedMåned = fraOgMedMåned,
                            sakInfo = sakInfo,
                            vedtakSomKanRevurderes = vedtak[sakInfo.sakId].orEmpty(),
                        )
                    }.getOrElse { feil ->
                        BleIkkeOmregnetAlder.FeilunderVurderingAvVedtakstilstand(
                            feil,
                            sakInfo.saksnummer,
                        ).left()
                    }
                }
            },
        )

        return sakerSomIkkeSkalVidere.map { it.left() } + vurderteSaker
    }

    private fun sjekkÅpneReguleringer(
        sakInfo: SakInfo,
        antallÅpne: Long,
    ): Either<BleIkkeOmregnetAlder, SakInfo> =
        when (antallÅpne) {
            0L -> sakInfo.right()
            1L -> BleIkkeOmregnetAlder.TrengerIkkeOmregne.FinnesÅpenOmregning(sakInfo.saksnummer).left()
            else -> BleIkkeOmregnetAlder.FlereÅpneReguleringer(
                saksnummer = sakInfo.saksnummer,
                antall = antallÅpne,
            ).left()
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
