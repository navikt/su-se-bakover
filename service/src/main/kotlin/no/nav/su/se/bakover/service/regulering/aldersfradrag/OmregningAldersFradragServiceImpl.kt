package no.nav.su.se.bakover.service.regulering.aldersfradrag

import BleIkkeOmregnetAlder
import OmregningAlderOppsummering
import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioderRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgang
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgangRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.logg
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.AapReguleringerService
import no.nav.su.se.bakover.service.regulering.AutomatiskTestRun
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import no.nav.su.se.bakover.service.regulering.SakBatchKjøring
import no.nav.su.se.bakover.service.regulering.aldersfradrag.OmregningAldersFradragService
import no.nav.su.se.bakover.service.regulering.grunnbeløp.HentEksterneBeløper
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import tilReguleringsresultat
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/**
 * Parallell til [ReguleringAutomatiskServiceImpl], men for automatisk omregning ved
 * endring i alderspensjon-fradrag (f.eks. satsendring på minstepensjon/Garantipensjon
 * i desember), fremfor grunnbeløpsregulering.
 */
class OmregningAldersFradragServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val reguleringKjøringRepo: ReguleringKjøringRepo,
    private val reguleringKjøringFremgangRepo: ReguleringKjøringFremgangRepo,
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
    private val reguleringService: ReguleringServiceImpl,
    private val satsFactory: SatsFactory,
    private val statistikkService: SakStatistikkService,
    private val sessionFactory: SessionFactory,
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val aapReguleringerService: AapReguleringerService,
    private val eksternReguleringPerioderRepo: EksternReguleringPerioderRepo,
) : OmregningAldersFradragService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Starter automatisk omregning av alle saker for en gitt måned.
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     */
    override fun startAutomatiskOmregning(
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>> =
        SakBatchKjøring.startAutomatisk(operasjonNavn = "omregning", log = log) {
            automatiskOmregningBatchvis(fraOgMedMåned, testRun = null)
        }

    override fun startAutomatiskOmregningForInnsyn(
        fraOgMedMåned: Måned,
        lagreManuelle: Boolean,
        maksAntallSaker: Int?,
        kunSakstype: Sakstype?,
    ): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>> =
        SakBatchKjøring.startAutomatisk(
            operasjonNavn = "omregning for innsyn",
            log = log,
        ) {
            automatiskOmregningBatchvis(
                fraOgMedMåned = fraOgMedMåned,
                testRun = AutomatiskTestRun(
                    lagreManuelle = lagreManuelle,
                    maksAntallSaker = maksAntallSaker,
                    kunSakstype = kunSakstype,
                ),
            )
        }

    /**
     * Henter saksinformasjon for alle saker og kjører dem batchvis via
     *
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     * @param testRun begrensninger for test-/innsynskjøringer, eller null for ordinær kjøring
     * @return ett resultat per sak (omregnet eller ikke omregnet)
     */
    private fun automatiskOmregningBatchvis(
        fraOgMedMåned: Måned,
        testRun: AutomatiskTestRun?,
    ): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>> {
        val startTid = LocalDateTime.now(clock)
        log.info("Automatisk omregning: Starter for måned=$fraOgMedMåned, dryrun=${testRun != null}")
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
            .let { saker -> testRun?.kunSakstype?.let { saker.filter { it.type == testRun.kunSakstype } } ?: saker }
            .let { saker -> testRun?.maksAntallSaker?.let { saker.take(it) } ?: saker }

        // SakBatchKjøring.kjør returnerer bare resultatene,
        // så vo tar vare på kjøringId fra callbacken for lagreResultat
        var sisteKjøringId: UUID? = null

        val resultater = SakBatchKjøring.kjør(
            navn = "automatisk-omregning-fradrag-alderspensjon",
            operasjonNavn = "omregning",
            log = log,
            alleSaker = alleSaker,
            metadata = mapOf(
                "fraOgMedMåned" to fraOgMedMåned.toString(),
                "dryrun" to (testRun != null).toString(),
            ),
            prosesserBatch = { batch, batchIndex, kjøringId ->
                sisteKjøringId = kjøringId
                batch.automatiskOmregningEnkeltBatch(fraOgMedMåned, kjøringId, batchIndex, testRun)
            },
            lagreFremgang = { kjøringId, batchIndex, antallSakerIBatch, batchResultater ->
                lagreBatchFremgang(kjøringId, batchIndex, antallSakerIBatch, batchResultater)
            },
        )

        return resultater.also {
            lagreResultat(fraOgMedMåned, startTid, testRun, alleSaker, it, sisteKjøringId!!)
        }
    }

    /**
     * Prosesserer én batch med saker gjennom omregningens steg, tilpasset til å operere
     * på [sakerPerBatch] i stedet for alle saker samlet, og til å returnere resultatet
     * i stedet for å lagre det selv.
     *
     * 1. Henter vedtaksdata for sakene
     * 2. Filtrerer til saker med aldeerspensjonsfradrag
     * 3. Henter eksternt regulerte beløp.
     * 4. Utfører automatisk omregningsbehandling per sak
     *
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     * @param kjøringId identifikator for den overordnede kjøringen
     * @param batchIndex indeks for denne batchen (0-basert)
     * @return ett resultat per sak i batchen
     */
    private fun List<SakInfo>.automatiskOmregningEnkeltBatch(
        fraOgMedMåned: Måned,
        kjøringId: UUID,
        batchIndex: Int,
        testRun: AutomatiskTestRun?,
    ): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>> {
        val sakerPerBatch = this

        // Steg1 : Henter vedtaksdata og filtrerer til saker som har alderspensjonsfradrag.
        val sakerMedAlderspensjonsfradrag =
            HentVedtaksdataForOmregningAlder(
                vedtakRepo = vedtakRepo,
                clock = clock,
            ).hent(
                saker = sakerPerBatch,
                fraOgMedMåned = fraOgMedMåned,
            )

        // Tar vare på omregningsresultatene som ikke skal videre
        val omregningsfeil = sakerMedAlderspensjonsfradrag.filterLefts()

        // Bare saker som fortsatt kan omregnes sendes videre for å hente eksterne beløp.
        val sakerSomSkalHentEksterneBeløp:
            List<Either<BleIkkeRegulert, SakTilRegulering>> =
            sakerMedAlderspensjonsfradrag.filterRights().map { it.right() }

        // Steg 3: Henter eksternt regulerte beløp som trengs før automatisk behandling
        val (sakerEtterEksterneBeløp, eksterntRegulerteBeløp) =
            HentEksterneBeløper(
                reguleringerFraPesysService,
                aapReguleringerService,
                eksternReguleringPerioderRepo,
                satsFactory,
            ).hent(
                saker = sakerSomSkalHentEksterneBeløp,
                fraOgMedMåned = fraOgMedMåned,
                kjøringId = kjøringId,
            )

        // Steg 4: Utfører selve omregningsbehandlingen per sak.
        val resultaterFraOmregning = UtførAutomatiskBehandlingOmregningAlder(
            reguleringService,
            reguleringRepo,
            satsFactory,
            statistikkService,
            sessionFactory,
            clock,
        ).utfør(
            saker = sakerEtterEksterneBeløp,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            testRun = testRun,
        )

        val resultater:
            List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>> =
            buildList {
                omregningsfeil.forEach {
                    add(Either.Left(it))
                }
                addAll(resultaterFraOmregning)
            }

        return resultater
    }

    /**
     * Aggregerer resultatene fra en komplett kjøring til en [ReguleringKjøring] og lagrer den.
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     * @param startTid tidspunktet kjøringen startet
     * @param testRun begrensninger for test-/innsynskjøringer, eller null for ordinær kjøring
     * @param alleSaker alle saker som ble vurdert
     * @param resultater ett resultat per sak
     * @param kjøringId identifikator for kjøringen
     */
    // Lagrer resultatet fra omregningskjøringen
    private fun lagreResultat(
        fraOgMedMåned: Måned,
        startTid: LocalDateTime,
        testRun: AutomatiskTestRun?,
        alleSaker: List<SakInfo>,
        resultater: List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>>,
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
            dryrun = testRun != null,
            startTid = startTid,
            sakerAntall = alleSaker.size,
            sakerIkkeLøpende = resultaterPerUtfall[Reguleringsresultat.Utfall.IKKE_LOEPENDE].orEmpty(),
            sakerAlleredeRegulert = emptyList(),
            sakerMåRevurderes = emptyList(),
            reguleringerSomFeilet = resultaterPerUtfall[Reguleringsresultat.Utfall.FEILET].orEmpty(),
            reguleringerAlleredeÅpen = resultaterPerUtfall[Reguleringsresultat.Utfall.AAPEN_REGULERING].orEmpty(),
            reguleringerManuell = resultaterPerUtfall[Reguleringsresultat.Utfall.MANUELL].orEmpty(),
            reguleringerAutomatisk = resultaterPerUtfall[Reguleringsresultat.Utfall.AUTOMATISK].orEmpty(),
        )
        // Lagrer kjøringen i databasen
        reguleringKjøringRepo.lagre(reguleringKjøring)
        log.info(reguleringKjøring.logg())
    }

    /**
     * Lagrer en snapshot av batchen i regulering_kjoring_fremgang.
     */
    private fun lagreBatchFremgang(
        kjøringId: UUID,
        batchIndex: Int,
        sakerIBatch: Int,
        batchResultater: List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>>,
    ) {
        Either.catch {
            reguleringKjøringFremgangRepo.lagre(
                ReguleringKjøringFremgang(
                    kjøringId = kjøringId,
                    batchNummer = batchIndex,
                    tidspunkt = Instant.now(clock),
                    sakerIBatch = sakerIBatch,
                    resultater = batchResultater.map { it.tilReguleringsresultat() },
                ),
            )
        }.onLeft {
            log.warn(
                "Automatisk omregning: Klarte ikke lagre fremgang for batch=$batchIndex, kjøringId=$kjøringId. Jobben fortsetter.",
                it,
            )
        }
    }
}
