package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.infrastructure.job.AktiveLangvarigeJobber
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioderRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.AapReguleringerService
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import no.nav.su.se.bakover.service.regulering.grunnbeløp.HentEksterneBeløper
import no.nav.su.se.bakover.service.regulering.grunnbeløp.HentVedtaksdataOgVurderOmReguleres
import no.nav.su.se.bakover.service.regulering.grunnbeløp.UtførAutomatiskBehandlingRegulering
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import satser.domain.SatsFactory
import tilReguleringsresultat
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

class OmregningAldersFradragServiceImpl(
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val reguleringRepo: ReguleringRepo,
    private val reguleringService: ReguleringServiceImpl,
    private val satsFactory: SatsFactory,
    private val clock: Clock,

    private val reguleringerFraPesysService: ReguleringerFraPesysService,

    private val aapReguleringerService: AapReguleringerService,

    private val eksternReguleringPerioderRepo: EksternReguleringPerioderRepo,

    private val sessionFactory: SessionFactory,

    private val statistikkService: SakStatistikkService,

    private val reguleringKjøringRepo: ReguleringKjøringRepo,

) {
    fun start(
        fraOgMedMåned: Måned,
    ) = AktiveLangvarigeJobber.kjør(
        navn = "regulering-omregning",
        metadata = mapOf(
            "fraOgMedMåned" to fraOgMedMåned.toString(),
        ),
    ) { kjøringId ->
        val startTid = LocalDateTime.now(clock)

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

        // Beholder Left-resultater fordi feil/ikke regulerte saker skal fortsatt være med i resultatflyten, men filtrerer bort Right-saker uten alderspensjonsfradrag
        val sakerMedAlderspensjonsfradrag = sakerEtterVurdering.filter { resultat ->
            resultat.fold(
                ifLeft = { true },
                ifRight = { sak ->
                    sak.gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.any {
                        it.fradragstype == Fradragstype.Alderspensjon
                    }
                },
            )
        }

        // Henter eksternt regulerte beløp som trengs før automatisk behandling
        val (sakerEtterEksterneBeløp, eksterntRegulerteBeløp) =
            HentEksterneBeløper(
                reguleringerFraPesysService,
                aapReguleringerService,
                eksternReguleringPerioderRepo,
                satsFactory,
            ).hent(
                saker = sakerMedAlderspensjonsfradrag,
                fraOgMedMåned = fraOgMedMåned,
                kjøringId = kjøringId,
            )
        val resultater =
            UtførAutomatiskBehandlingRegulering(
                reguleringService,
                reguleringRepo,
                satsFactory,
                sessionFactory,
                statistikkService,
                clock,
            ).utfør(
                saker = sakerEtterEksterneBeløp,
                eksterntRegulerteBeløp = eksterntRegulerteBeløp,
                testRun = null,
            )

        lagreResultat(
            fraOgMedMåned = fraOgMedMåned,
            startTid = startTid,
            alleSaker = alleSaker,
            resultater = resultater,
            kjøringId = kjøringId,
        )

        // TODO: vurder om det finnes saker som må behandles manuelt
    }

    // Lagrer resultatet fra omregningskjøringen
    private fun lagreResultat(
        fraOgMedMåned: Måned,
        startTid: LocalDateTime,
        alleSaker: List<SakInfo>,
        resultater: List<Either<BleIkkeRegulert, ReguleringOppsummering>>,
        kjøringId: UUID,
    ) {
        // Mapper resultatene til ReguleringResultat og grupperer dem på utfall
        val resultaterPerUtfall =
            resultater
                .map { it.tilReguleringsresultat() }
                .groupBy { it.utfall }

        // Lagrer oppsummeringen for hele omregningskjøringen
        val reguleringKjøring = ReguleringKjøring(
            id = kjøringId,
            aar = fraOgMedMåned.årOgMåned.year,
            type = ReguleringKjøring.REGULERINGSTYPE_ALDERSFRADRAG,
            dryrun = false,
            startTid = startTid,
            sakerAntall = alleSaker.size,
            sakerIkkeLøpende = resultaterPerUtfall[Reguleringsresultat.Utfall.IKKE_LOEPENDE].orEmpty(),
            sakerAlleredeRegulert = resultaterPerUtfall[Reguleringsresultat.Utfall.ALLEREDE_REGULERT].orEmpty(),
            sakerMåRevurderes = resultaterPerUtfall[Reguleringsresultat.Utfall.MÅ_REVURDERE].orEmpty(),
            reguleringerSomFeilet = resultaterPerUtfall[Reguleringsresultat.Utfall.FEILET].orEmpty(),
            reguleringerAlleredeÅpen = resultaterPerUtfall[Reguleringsresultat.Utfall.AAPEN_REGULERING].orEmpty(),
            reguleringerManuell = resultaterPerUtfall[Reguleringsresultat.Utfall.MANUELL].orEmpty(),
            reguleringerAutomatisk = resultaterPerUtfall[Reguleringsresultat.Utfall.AUTOMATISK].orEmpty(),
        )
        // Lagrer kjøringen i databasen
        reguleringKjøringRepo.lagre(reguleringKjøring)
    }
}
