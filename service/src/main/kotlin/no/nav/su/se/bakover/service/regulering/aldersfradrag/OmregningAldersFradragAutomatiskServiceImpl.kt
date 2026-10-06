package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgang
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgangRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.logg
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.AutomatiskTestRun
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import no.nav.su.se.bakover.service.regulering.SakBatchKjøring
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/**
 * Parallell til [ReguleringGrunnbeløpAutomatiskServiceImpl], men for automatisk omregning ved
 * endring i alderspensjon-fradrag (f.eks. satsendring på minstepensjon/Garantipensjon
 * i desember), fremfor grunnbeløpsregulering.
 */
class OmregningAldersFradragAutomatiskServiceImpl(
    private val reguleringKjøringRepo: ReguleringKjøringRepo,
    private val reguleringKjøringFremgangRepo: ReguleringKjøringFremgangRepo,
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
    private val reguleringService: ReguleringServiceImpl,
    private val satsFactory: SatsFactory,
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
) : OmregningAldersFradragAutomatiskService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Starter automatisk omregning av alle saker for en gitt måned.
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     */
    override fun startAutomatiskOmregning(
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>> =
        SakBatchKjøring.startAutomatisk(operasjonNavn = "omregning", log = log) {
            automatiskOmregningBatchvis(fraOgMedMåned, testRun = null)
        }

    override fun startAutomatiskOmregningForInnsyn(
        fraOgMedMåned: Måned,
        lagreManuelle: Boolean,
        maksAntallSaker: Int?,
        saksnummer: String?,
    ): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>> =
        SakBatchKjøring.startAutomatisk(
            operasjonNavn = "omregning for innsyn",
            log = log,
        ) {
            automatiskOmregningBatchvis(
                fraOgMedMåned = fraOgMedMåned,
                testRun = AutomatiskTestRun(
                    lagreManuelle = lagreManuelle,
                    maksAntallSaker = maksAntallSaker,
                    kunSakstype = Sakstype.ALDER,
                    saksnummer = saksnummer,

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
    ): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>> {
        val startTid = LocalDateTime.now(clock)
        log.info("Automatisk omregning: Starter for måned=$fraOgMedMåned, dryrun=${testRun != null}")
        val saksnummer = testRun?.saksnummer?.let {
            Saksnummer.tryParse(it).getOrElse {
                throw IllegalStateException("Ugyldig saksnummer: ${testRun.saksnummer}")
            }
        }
        saksnummer?.let {
            sakService.hentSak(it).getOrElse {
                throw IllegalStateException("Fant ikke sak med saksnummer: $saksnummer")
            }
        }
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
            .let { saker ->
                testRun?.kunSakstype?.let {
                    saker.filter { it.type == testRun.kunSakstype }
                } ?: saker
            }
            .let { saker ->
                testRun?.saksnummer?.let { saksnummer ->
                    saker.filter { it.saksnummer.toString() == saksnummer }
                } ?: saker
            }
            .let { saker ->
                testRun?.maksAntallSaker?.let {
                    saker.take(it)
                } ?: saker
            }

        // SakBatchKjøring.kjør returnerer bare resultatene,
        // så vi tar vare på kjøringId fra callbacken for lagreResultat
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
            onKjøringStart = { kjøringId ->
                sisteKjøringId = kjøringId
            },
            prosesserBatch = { batch, _ ->
                batch.automatiskOmregningEnkeltBatch(fraOgMedMåned, testRun)
            },
            lagreFremgang = { kjøringId, batchIndex, antallSakerIBatch, batchResultater ->
                lagreBatchFremgang(kjøringId, batchIndex, antallSakerIBatch, batchResultater)
            },
        )

        return resultater.also {
            lagreResultat(fraOgMedMåned, startTid, testRun, alleSaker, it, requireNotNull(sisteKjøringId))
        }
    }

    /**
     * Prosesserer én batch med saker gjennom omregningsflyten
     *
     * 1. Henter vedtaksdata og filtrerer til saker med alderspensjonsfradrag.
     * 2. Henter kun alderspensjonsbeløp fra PESYS.
     * 3. Utfører automatisk omregningsbehandling per sak
     *
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     * @return ett resultat per sak i batchen
     */
    private fun List<SakInfo>.automatiskOmregningEnkeltBatch(
        fraOgMedMåned: Måned,
        testRun: AutomatiskTestRun?,
    ): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>> {
        val sakerPerBatch = this

        // Steg 1 : Henter vedtaksdata og filtrerer til saker som har alderspensjonsfradrag.
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

        val sakerSomSkalHentEksterneBeløp =
            sakerMedAlderspensjonsfradrag
                .filterRights()
                .map { it.right() }

        // Steg 2: Henter kun alderspensjonsbeløp fra PESYS.
        val (sakerEtterEksterneBeløp, eksterntRegulerteBeløp) =
            HentEksterneBeløperFraOmregningAlder(
                reguleringerFraPesysService,
                satsFactory,
            ).hent(
                saker = sakerSomSkalHentEksterneBeløp,
                fraOgMedMåned = fraOgMedMåned,
            )

        // Steg 3: Utfører selve omregningsbehandlingen per sak.
        val resultaterFraOmregning = UtførAutomatiskBehandlingOmregningAlder(
            reguleringService,
            satsFactory,
            clock,
        ).utfør(
            saker = sakerEtterEksterneBeløp,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            testRun = testRun,
        )

        val resultater:
            List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>> =
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
        resultater: List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>>,
        kjøringId: UUID,
    ) {
        // Mapper resultatene til ReguleringResultat og grupperer dem på utfall
        val resultaterPerUtfall =
            resultater
                .map { it.tilReguleringsresultat() }
                .groupBy { it.utfall }

        resultater.map { it.tilReguleringsresultat() }.forEach {
            log.info(
                "Omregning resultat: saksnummer=${it.saksnummer}," +
                    "utfall=${it.utfall}, beskrivelse=${it.beskrivelse}",
            )
        }

        val reguleringKjøring = ReguleringKjøring.Aldersfradrag(
            id = kjøringId,
            aar = fraOgMedMåned.årOgMåned.year,
            dryrun = testRun != null,
            startTid = startTid,
            sakerAntall = alleSaker.size,
            sakerIkkeLøpende = resultaterPerUtfall[Reguleringsresultat.Utfall.IKKE_LOEPENDE].orEmpty(),
            reguleringerSomFeilet = resultaterPerUtfall[Reguleringsresultat.Utfall.FEILET].orEmpty(),
            reguleringerAlleredeÅpen = resultaterPerUtfall[Reguleringsresultat.Utfall.AAPEN_REGULERING].orEmpty(),
            reguleringerManuell = resultaterPerUtfall[Reguleringsresultat.Utfall.MANUELL].orEmpty(),
            skalIkkeOmregnes = resultaterPerUtfall[Reguleringsresultat.Utfall.SKAL_IKKE_OMREGNES].orEmpty(),
        )
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
        batchResultater: List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>>,
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

fun Either<BleIkkeOmregnetAlder, ReguleringOppsummering>.tilReguleringsresultat(): Reguleringsresultat =
    fold(
        ifLeft = { bleIkkeOmregnet ->
            when (bleIkkeOmregnet) {
                is BleIkkeOmregnetAlder.TrengerIkkeOmregne.IkkeLøpendeSak -> Reguleringsresultat(
                    saksnummer = bleIkkeOmregnet.saksnummer,
                    behandlingsId = null,
                    utfall = Reguleringsresultat.Utfall.IKKE_LOEPENDE,
                    beskrivelse = bleIkkeOmregnet.toString(),
                )
                is BleIkkeOmregnetAlder.HarIkkeAlderspensjonFradrag ->
                    Reguleringsresultat(
                        saksnummer = bleIkkeOmregnet.saksnummer,
                        behandlingsId = null,
                        utfall = Reguleringsresultat.Utfall.FEILET,
                        beskrivelse = bleIkkeOmregnet.toString(),
                    )
                is BleIkkeOmregnetAlder.UthentingFradragEksterntFeilet ->
                    Reguleringsresultat(
                        saksnummer = bleIkkeOmregnet.saksnummer,
                        behandlingsId = null,
                        utfall = Reguleringsresultat.Utfall.FEILET,
                        beskrivelse = bleIkkeOmregnet.toString(),
                    )

                is BleIkkeOmregnetAlder.KunneIkkeBehandleAutomatisk ->
                    Reguleringsresultat(
                        saksnummer = bleIkkeOmregnet.saksnummer,
                        behandlingsId = null,
                        utfall = Reguleringsresultat.Utfall.FEILET,
                        beskrivelse = bleIkkeOmregnet.toString(),
                    )
            }
        },
        ifRight = { oppsummering ->
            when (val type = oppsummering.reguleringstype) {
                Reguleringstype.AUTOMATISK -> throw IllegalStateException("Automatisk omregning av alderspensjon skal avsluttes manuelt")
                is Reguleringstype.MANUELL -> {
                    Reguleringsresultat(
                        saksnummer = oppsummering.saksnummer,
                        behandlingsId = oppsummering.behandlingsId,
                        utfall = Reguleringsresultat.Utfall.MANUELL,
                        beskrivelse = type.problemer.map { it.begrunnelse ?: "" }.single(),
                    )
                }
            }
        },
    )
