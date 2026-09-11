package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioderRepo
import no.nav.su.se.bakover.domain.regulering.OmregningAldersFradragService
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgang
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgangRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.logg
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.AapReguleringerService
import no.nav.su.se.bakover.service.regulering.AutomatiskTestRun
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import no.nav.su.se.bakover.service.regulering.SakBatchKjøring
import no.nav.su.se.bakover.service.regulering.grunnbeløp.HentEksterneBeløper
import no.nav.su.se.bakover.service.regulering.grunnbeløp.HentVedtaksdataOgVurderOmReguleres
import no.nav.su.se.bakover.service.regulering.grunnbeløp.UtførAutomatiskBehandlingRegulering
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import tilReguleringsresultat
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

// DRAFT — parallell struktur til ReguleringAutomatiskServiceImpl, jf. avtale om at
// konstruktør/rekkefølge/navngivning skal være mest mulig identisk for gjenbruk.
// Ingenting er fjernet fra den parallelle konstruktøren selv om noe kanskje ikke
// trengs for omregning — usikre punkter er kommentert, ikke fjernet, til diskusjon.
//
// Denne versjonen bruker de nye, foreslåtte delte klassene:
//   - SakBatchKjøring (batching/parallellitet + ytre try/catch-lag)
//   - AutomatiskTestRun (felles dryrun-konfigurasjon, erstatter ReguleringTestRun)
// Resultat-mapping bruker tilReguleringsresultat() (den ekte, delte funksjonen i
// ReguleringsresultatMapper.kt) i stedet for den tidligere draft-varianten
// tilAutomatiskResultat() — for konsistens med ReguleringGrunnbeløpServiceImpl.
// ReguleringGrunnbeløpServiceImpl er IKKE endret til å bruke SakBatchKjøring/
// AutomatiskTestRun ennå — det er planlagt som egen, senere omgang, etter at dette
// mønsteret er bevist her.

/**
 * Parallell til [ReguleringAutomatiskServiceImpl], men for automatisk omregning ved
 * endring i alderspensjon-fradrag (f.eks. satsendring på minstepensjon/Garantipensjon
 * i desember), fremfor grunnbeløpsregulering.
 *
 * Denne bygges og testes først; dersom mønsteret fungerer, tilpasses
 * [ReguleringAutomatiskServiceImpl] etterpå til å bruke samme delte klasser.
 */
class OmregningAldersFradragServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val reguleringKjøringRepo: ReguleringKjøringRepo,
    private val reguleringKjøringFremgangRepo: ReguleringKjøringFremgangRepo,
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
    private val reguleringService: ReguleringServiceImpl,
    private val statistikkService: SakStatistikkService,
    private val sessionFactory: SessionFactory,
    // DISKUSJON: reguleringerFraPesysService og aapReguleringerService brukes i dag
    // (i regulering-flyten) av HentEksterneBeløper (steg 2) for å hente eksterne
    // regulerte beløp fra Pesys/AAP. Uklart ennå om omregning ved alderspensjon-
    // fradragsendring trenger disse samme oppslagene, eller om den trenger et helt
    // annet Pesys-oppslag (nytt alderspensjonsbeløp). Beholdt her for parallell
    // struktur — ikke fjernet, ikke bekreftet nødvendig eller unødvendig ennå.
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val aapReguleringerService: AapReguleringerService,
    private val eksternReguleringPerioderRepo: EksternReguleringPerioderRepo,
) : OmregningAldersFradragService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Starter automatisk omregning av alle saker for en gitt måned.
     *
     * Parallell til [ReguleringAutomatiskServiceImpl.startAutomatiskRegulering], men
     * bruker [SakBatchKjøring.startAutomatisk] for det delte try/catch+logging-laget
     * i stedet for å duplisere det.
     *
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     */
    override fun startAutomatiskOmregning(
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> =
        SakBatchKjøring.startAutomatisk(operasjonNavn = "omregning", log = log) {
            automatiskOmregningBatchvis(fraOgMedMåned, satsFactory, testRun = null)
        }

    /**
     * Starter automatisk omregning for innsyn, en testkjøring (dryrun).
     *
     * Parallell til [ReguleringAutomatiskServiceImpl.startAutomatiskReguleringForInnsyn].
     * Bruker [AutomatiskTestRun] (felles med regulering) for å begrense omfanget
     * (kun sakstype, maks antall saker, om manuelle omregninger skal lagres).
     *
     * DISKUSJON: mangler foreløpig en egen satsFactory/dato-håndtering slik
     * [no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand]
     * har for grunnbeløp — se `StartAutomatiskOmregningForInnsynCommand-draft.kt` for
     * forslag til kommando-klasse. Ikke koblet på her ennå.
     */
    fun startAutomatiskOmregningForInnsyn(
        fraOgMedMåned: Måned,
        lagreManuelle: Boolean,
        maksAntallSaker: Int?,
        kunSakstype: Sakstype?,
    ) {
        SakBatchKjøring.startAutomatisk(operasjonNavn = "omregning for innsyn", log = log) {
            automatiskOmregningBatchvis(
                fraOgMedMåned = fraOgMedMåned,
                satsFactory = satsFactory,
                testRun = AutomatiskTestRun(
                    lagreManuelle = lagreManuelle,
                    maksAntallSaker = maksAntallSaker,
                    kunSakstype = kunSakstype,
                ),
            )
        }
    }

    /**
     * Henter saksinformasjon for alle saker og kjører dem batchvis via
     * [SakBatchKjøring.kjør] — samme delte motor som (foreløpig kun her,
     * ikke i [ReguleringAutomatiskServiceImpl] ennå) håndterer chunking og
     * parallellitet.
     *
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     * @param satsFactory fabrikk for gjeldende satser
     * @param testRun begrensninger for test-/innsynskjøringer, eller null for ordinær kjøring
     * @return ett resultat per sak (omregnet eller ikke omregnet)
     */
    private fun automatiskOmregningBatchvis(
        fraOgMedMåned: Måned,
        satsFactory: SatsFactory,
        testRun: AutomatiskTestRun?,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> {
        val startTid = LocalDateTime.now()
        log.info("Automatisk omregning: Starter for måned=$fraOgMedMåned, dryrun=${testRun != null}")
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
            .let { saker -> testRun?.kunSakstype?.let { saker.filter { it.type == testRun.kunSakstype } } ?: saker }
            .let { saker -> testRun?.maksAntallSaker?.let { saker.take(it) } ?: saker }

        // DISKUSJON: SakBatchKjøring.kjør(...) gir kun kjøringId inn i
        // callbackene (prosesserBatch/lagreFremgang), ikke tilbake til den ytre
        // kalleren etter at alt er ferdig. Samme kjøringId brukes for alle batcher i
        // én kjøring, så det holder å huske den siste vi mottar, for å kunne bruke
        // den i lagreResultat nedenfor.
        var sisteKjøringId: UUID? = null

        val resultater = SakBatchKjøring.kjør(
            navn = "automatisk-omregning",
            operasjonNavn = "omregning",
            log = log,
            alleSaker = alleSaker,
            metadata = mapOf(
                "fraOgMedMåned" to fraOgMedMåned.toString(),
                "dryrun" to (testRun != null).toString(),
            ),
            prosesserBatch = { batch, batchIndex, kjøringId ->
                sisteKjøringId = kjøringId
                batch.automatiskOmregningEnkeltBatch(fraOgMedMåned, satsFactory, kjøringId, batchIndex)
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
     * 1. Henter vedtaksdata og filtrerer bort saker som ikke skal reguleres.
     * 2. Filtrerer videre til kun saker med alderspensjon-fradrag.
     * 3. Henter eksternt regulerte beløp.
     * 4. Utfører selve omregningsbehandlingen per sak.
     *
     * @param fraOgMedMåned måneden omregningen gjelder fra og med
     * @param satsFactory fabrikk for gjeldende satser
     * @param kjøringId identifikator for den overordnede kjøringen
     * @param batchIndex indeks for denne batchen (0-basert)
     * @return ett resultat per sak i batchen
     */
    private fun List<SakInfo>.automatiskOmregningEnkeltBatch(
        fraOgMedMåned: Måned,
        satsFactory: SatsFactory,
        kjøringId: UUID,
        batchIndex: Int,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> {
        val sakerPerBatch = this

        // Steg 1: Henter vedtaksdata og filtrerer bort saker som ikke skal reguleres.
        val sakerEtterVurdering =
            HentVedtaksdataOgVurderOmReguleres(
                satsFactory = satsFactory,
                clock = clock,
                reguleringRepo = reguleringRepo,
                vedtakRepo = vedtakRepo,
            ).hent(
                saker = sakerPerBatch,
                fraOgMedMåned = fraOgMedMåned,
                grunnbeløpRegulering = false,
            )

        // Steg 2: Beholder Left-resultater fordi feil/ikke regulerte saker skal fortsatt være med i resultatflyten, men filtrerer bort Right-saker uten alderspensjonsfradrag
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

        // Steg 3: Henter eksternt regulerte beløp som trengs før automatisk behandling
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

        // Steg 4: Utfører selve omregningsbehandlingen per sak.
        return UtførAutomatiskBehandlingRegulering(
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

        // TODO: vurder om det finnes saker som må behandles manuelt.
    }

    /**
     * Aggregerer resultatene fra en komplett kjøring til en [ReguleringKjøring] og lagrer den.
     * Parallell til [ReguleringGrunnbeløpServiceImpl.lagreResultat].
     *
     * DISKUSJON: bruker `ReguleringKjøring.REGULERINGSTYPE_ALDERSFRADRAG`, se `ReguleringKjøring.kt`.
     *
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
            dryrun = testRun != null,
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
        log.info(reguleringKjøring.logg())
    }

    /**
     * Lagrer en snapshot av batchen i regulering_kjoring_fremgang. Parallell til
     * [ReguleringAutomatiskServiceImpl.lagreBatchFremgang].
     */
    private fun lagreBatchFremgang(
        kjøringId: UUID,
        batchIndex: Int,
        sakerIBatch: Int,
        batchResultater: List<Either<BleIkkeRegulert, ReguleringOppsummering>>,
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
