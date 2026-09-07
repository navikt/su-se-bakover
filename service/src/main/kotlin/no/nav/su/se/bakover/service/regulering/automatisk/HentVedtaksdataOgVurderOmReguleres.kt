package no.nav.su.se.bakover.service.regulering.automatisk

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.erRegulertMedNyttGrunnbeløp
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import satser.domain.SatsFactory
import java.time.Clock

internal class HentVedtaksdataOgVurderOmReguleres(
    private val satsFactory: SatsFactory,
    private val clock: Clock,
    private val reguleringRepo: ReguleringRepo,
    private val vedtakRepo: VedtakRepo,
) {
    /**
     * Henter vedtaksdata og vurderer om hver sak skal reguleres.
     *
     * @param saker sakene som skal vurderes
     * @param fraOgMedMåned måneden reguleringen gjelder fra og med
     * @param grunnbeløpRegulering om det er en grunnbeløpsregulering
     * @return ett resultat per sak:
     *  [BleIkkeRegulert.TrengerIkkeRegulere] hvis sak ikke skal reguleres
     *  [BleIkkeRegulert.ReguleringFeiletVedKlargjøring.UthentingAvVedtakFeilet] hvis sak ikke skal reguleres
     *  [SakTilRegulering] Hvis sak skal reguleres
     */
    fun hent(
        saker: List<SakInfo>,
        fraOgMedMåned: Måned,
        grunnbeløpRegulering: Boolean,
    ): List<Either<BleIkkeRegulert, SakTilRegulering>> = saker.map { sakInfo ->
        Either.catch {
            hentVedtaksdataOgVurderOmSkalRegulere(
                fraOgMedMåned,
                sakInfo,
                grunnbeløpRegulering,
            )
        }.getOrElse { feil ->
            BleIkkeRegulert.ReguleringFeiletVedKlargjøring.UthentingAvVedtakFeilet(feil, sakInfo.saksnummer).left()
        }
    }

    /**
     * Vurderer én sak og henter vedtaksdata for regulering.
     *
     * Saken utelukkes fra regulering dersom det finnes en åpen regulering ([ReguleringUnderBehandling]),
     * den allerede er regulert for gjeldende måned (ved grunnbeløpsregulering), eller alle
     * vedtaksperiodene allerede er beregnet med nytt grunnbeløp. Ellers returneres saken med
     * gjeldende vedtaksdata som en [SakTilRegulering].
     *
     * @return enten [BleIkkeRegulert] (saken skal ikke reguleres) eller en [SakTilRegulering]
     *         med gjeldende vedtaksdata
     * @throws IllegalStateException dersom det finnes flere enn én åpen regulering, eller dersom
     *         forventet vedtak/månedsberegning mangler for en periode
     */
    private fun hentVedtaksdataOgVurderOmSkalRegulere(
        fraOgMedMåned: Måned,
        sakInfo: SakInfo,
        grunnbeløpRegulering: Boolean,
    ): Either<BleIkkeRegulert, SakTilRegulering> {
        val (sakid, saksnummer, _, type) = sakInfo
        val reguleringer = reguleringRepo.hentForSakId(sakid)
        reguleringer.filterIsInstance<ReguleringUnderBehandling>().let { r ->
            when (r.size) {
                0 -> {}
                1 -> return BleIkkeRegulert.TrengerIkkeRegulere.FinnesÅpenRegulering(saksnummer)
                    .left()

                else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende grunn: Det finnes fler enn en åpen regulering.")
            }
        }
        if (grunnbeløpRegulering) {
            val alleredeRegulert = reguleringer.filterIsInstance<IverksattRegulering>()
                .any { it.periode.fraOgMed == fraOgMedMåned.fraOgMed }
            if (alleredeRegulert) {
                return BleIkkeRegulert.TrengerIkkeRegulere.AlleredeRegulert(saksnummer).left()
            }
        }

        val vedtakSomKanRevurderes = vedtakRepo.hentVedtakSomKanRevurderesForSakFraOgMed(sakInfo.sakId, fraOgMedMåned)
        val vedtaksdata =
            hentGjeldendeVedtaksdataForRegulering(
                fraOgMedMåned,
                sakInfo,
                vedtakSomKanRevurderes,
                clock,
            ).getOrElse {
                return it.left()
            }

        if (grunnbeløpRegulering) {
            val sisteBeløp = satsFactory.grunnbeløpOgGarantipensjon(fraOgMedMåned)
            if (vedtaksdata.vedtaksperioder.all { vedtaksperiode ->
                    val vedtakPåMåned = vedtaksdata.gjeldendeVedtakPåDato(vedtaksperiode.fraOgMed)
                        ?: throw IllegalStateException("Forventer at det finnes et gjeldende vedtak for hver periode. saksnummer=${sakInfo.saksnummer}")

                    if (vedtakPåMåned.erStans() || vedtakPåMåned.erGjenopptak()) {
                        val sisteVedtakMedBeregning =
                            vedtakRepo.hentBeregninginfoTilVedtakPåDato(sakInfo, vedtaksperiode.fraOgMed)
                        sisteBeløp.erRegulertMedNyttGrunnbeløp(type, sisteVedtakMedBeregning)
                    } else {
                        val månedsberegning = vedtaksdata.hentMånedsberegning(vedtaksperiode).firstOrNull()
                            ?: throw (IllegalStateException("Forventer minst én månedsberegning per periode. saksnummer=${sakInfo.saksnummer}"))
                        sisteBeløp.erRegulertMedNyttGrunnbeløp(type, månedsberegning)
                    }
                }
            ) {
                return BleIkkeRegulert.TrengerIkkeRegulere.AlleredeRegulert(saksnummer).left()
            }
        }

        return SakTilRegulering(sakInfo, vedtaksdata).right()
    }
}
