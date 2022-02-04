package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.regulering.BehandlingType
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.VedtakSomKanReguleres
import no.nav.su.se.bakover.domain.regulering.VedtakType
import java.time.LocalDate

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
) : ReguleringService {

    override fun hentAlleSakerSomKanReguleres(fraDato: LocalDate): Either<KanIkkeHenteSaker, SakerSomKanReguleres> {
        return SakerSomKanReguleres(
            saker = hentAlleSaker(fraDato),
        ).right()
    }

    private fun hentVedtakSomKanReguleres(fomDato: LocalDate): List<VedtakSomKanReguleres> {
        return reguleringRepo.hentVedtakSomKanReguleres(fomDato)
    }

    private fun hentAlleSaker(fraDato: LocalDate): List<SakSomKanReguleres> {
        return hentVedtakSomKanReguleres(fraDato)
            .filterNot { it.vedtakType == VedtakType.AVSLAG || it.vedtakType == VedtakType.AVVIST_KLAGE }
            .groupBy { it.sakId }
            .mapNotNull { (sakid, vedtakSomKanReguleres) ->
                val minFra: LocalDate = maxOf(vedtakSomKanReguleres.minOf { it.fraOgMed }, fraDato)
                val maxTil: LocalDate = vedtakSomKanReguleres.maxOf { it.tilOgMed }

                val gjeldendeVedtakPrMnd = Periode.create(minFra, maxTil).tilMånedsperioder().map { mnd ->
                    mnd to vedtakSomKanReguleres.filter {
                        val vedtaksperiode = Periode.create(it.fraOgMed, it.tilOgMed)
                        vedtaksperiode.inneholder(mnd)
                    }.maxByOrNull { it.opprettet.instant }!!
                }.filterNot { it.second.vedtakType == VedtakType.OPPHØR }.ifEmpty {
                    return@mapNotNull null
                }

                val type =
                    if (gjeldendeVedtakPrMnd.any { it.second.behandlingType == BehandlingType.MANUELL || it.second.vedtakType == VedtakType.STANS_AV_YTELSE }) {
                        BehandlingType.MANUELL
                    } else BehandlingType.AUTOMATISK

                SakSomKanReguleres(
                    sakId = sakid,
                    saksnummer = vedtakSomKanReguleres.first().saksnummer,
                    type = type,
                )
            }
    }
}
