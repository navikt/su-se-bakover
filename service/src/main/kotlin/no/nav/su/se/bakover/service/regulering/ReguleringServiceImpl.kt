package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.VedtakSomKanReguleres
import no.nav.su.se.bakover.domain.regulering.VedtakType
import java.time.LocalDate

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
) : ReguleringService {

    override fun hentAlleSakerSomKanReguleres(fraDato: LocalDate?): SakerSomKanReguleres {
        return SakerSomKanReguleres(
            saker = hentAlleSaker(fraDato ?: 1.mai(LocalDate.now().year)),
        )
    }

    private fun hentVedtakSomKanReguleres(fraOgMed: LocalDate): List<VedtakSomKanReguleres> {
        return reguleringRepo.hentVedtakSomKanReguleres(fraOgMed)
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
                    if (gjeldendeVedtakPrMnd.any { it.second.reguleringType == ReguleringType.MANUELL || it.second.vedtakType == VedtakType.STANS_AV_YTELSE }) {
                        ReguleringType.MANUELL
                    } else ReguleringType.AUTOMATISK

                SakSomKanReguleres(
                    sakId = sakid,
                    saksnummer = vedtakSomKanReguleres.first().saksnummer,
                    reguleringType = type,
                )
            }
    }
}
