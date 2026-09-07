package no.nav.su.se.bakover.service.regulering.omregning

import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.service.regulering.automatisk.HentVedtaksdataOgVurderOmReguleres
import satser.domain.SatsFactory
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock

class ReguleringOmregningServiceImpl(
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val reguleringRepo: ReguleringRepo,
    private val reguleringService: ReguleringService,
    private val satsFactory: SatsFactory,
    private val clock: Clock,
) {
    fun start(
        fraOgMedMåned: Måned,
    ) {
        // Henter alle saker som vi senere skal filtrere ned
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()

        // Henter vedtaksdata og filtrerer bort saker som ikke skal reguleres.
        val sakerEtterVurdering =
            HentVedtaksdataOgVurderOmReguleres(
                satsFactory = satsFactory,
                clock = clock,
                reguleringRepo = reguleringRepo,
                vedtakRepo = vedtakRepo,
            ).hent(
                saker = alleSaker,
                fraOgMedMåned = fraOgMedMåned,
                grunnbeløpRegulering = false,
            )

        // Henter ut bare sakene som faktisk kan reguleres videre
        val sakerSomKanReguleres = sakerEtterVurdering.filterRights()

        // Går gjennom sakene som kan reguleres og beholder bare de som har minst ett Alderspensjon fradrag
        val sakerMedAlderspensjonsfradrag =
            sakerSomKanReguleres.filter { sak ->
                sak.gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.any {
                    it.fradragstype == Fradragstype.Alderspensjon
                }
            }

        // TODO: vurder om det finnes saker som må behandles manuelt
        val sakerSomKanBehandlesAutomatisk = sakerMedAlderspensjonsfradrag
    }

    // sjekker om saken har fradrag for alderspensjon i vedtaksdataene
    private fun harAlderspensjonsfradrag(
        vedtakSomKanRevurderes: List<VedtakSomKanRevurderes>,
    ): Boolean {
        return vedtakSomKanRevurderes
            .lagTidslinje()
            ?.flatMap { it.grunnlagsdata.fradragsgrunnlag }
            ?.any { it.fradragstype == Fradragstype.Alderspensjon }
            ?: false
    }
}
