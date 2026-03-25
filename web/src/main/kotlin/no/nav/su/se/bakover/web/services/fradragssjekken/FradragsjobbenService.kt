package no.nav.su.se.bakover.web.services.fradragssjekken

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumVedtakDto
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPeriode
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.correlation.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

interface FradragsjobbenService {
    fun sjekkLøpendeSakerForFradragIEksterneSystemer()
}

private const val INTERN_SAK_BATCH_STORRELSE = 500
private const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50
private const val AAP_PARALLELLE_OPPSLAG = 8
private const val BELOPS_TOLERANSE = 10
private val AAP_STONADSDAGER_PER_AR = BigDecimal(260)
private val AAP_MANEDER_PER_AR = BigDecimal(12)

class FradragsjobbenServiceImpl(
    private val aapKlient: AapApiInternClient,
    private val pesysKlient: PesysClient,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val utbetalingsRepo: UtbetalingRepo,
    private val clock: Clock,
) : FradragsjobbenService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * 1. Finn løpende saker
     * 2. Hent fradrag for sakene basert på type sak
     * SU ufør: Sjekk UFØR OG AAP på bruker og UFØR og AAP på EPS hvis EPS er under 67, men kun AP for EPS dersom EPS er over 67
     *
     * SU alder: sjekk AP på bruker og AP på EPS dersom EPS er over 67, men UFØR og AAP dersom EPS er under 67
     * 3. Lag oppgave hvis fradrag er ulikt eller ikke fantes på brukes
     *
     */
    override fun sjekkLøpendeSakerForFradragIEksterneSystemer() {
        val måned = Måned.now(clock)
        var resultat = FradragssjekkResultat()

        hentAlleSaker()
            .chunked(INTERN_SAK_BATCH_STORRELSE)
            .forEach { sakerPerBatch ->
                val løpendeSaker = filtrerSakerMedLøpendeUtbetalingForMåned(sakerPerBatch, måned)
                lagSjekkplanerForLøpendeSaker(løpendeSaker, måned)
                    .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
                    .forEach { sjekkplanBatch ->
                        resultat += prosesserSjekkplanBatch(sjekkplanBatch, måned)
                    }
            }

        log.info(
            "Fradragssjekk fullført for måned {}. Vurderte saker: {}, saker med avvik: {}, opprettede oppgaver: {}, hoppet over pga eksterne feil: {}",
            måned,
            resultat.vurderteSaker,
            resultat.sakerMedAvvik,
            resultat.opprettedeOppgaver,
            resultat.hoppetOverPåGrunnAvEksternFeil,
        )

        if (resultat.mislykkedeOppgaveopprettelser.isNotEmpty()) {
            log.error(
                "Fradragssjekk: Mislykket oppgaveopprettelse for {} saker. {}",
                resultat.mislykkedeOppgaveopprettelser.size,
                resultat.mislykkedeOppgaveopprettelser.joinToString(separator = "; ") {
                    "sakId=${it.sakId}, avvikskoder=${it.avvikskoder.joinToString(",")}"
                },
            )
        }
    }

    private fun hentAlleSaker() = sakService.hentSakIdSaksnummerOgFnrForAlleSaker()

    private fun filtrerSakerMedLøpendeUtbetalingForMåned(
        saker: List<SakInfo>,
        måned: Måned,
    ): List<SakInfo> {
        if (saker.isEmpty()) return emptyList()

        val utbetalingerPerSak = utbetalingsRepo.hentOversendteUtbetalingerForSakIder(
            saker.map { it.sakId },
        )

        return saker.filter { sak ->
            utbetalingerPerSak[sak.sakId]
                ?.hentGjeldendeUtbetaling(måned.fraOgMed)
                ?.fold(
                    { false },
                    { true },
                ) == true
        }
    }

    private fun lagSjekkplanerForLøpendeSaker(
        løpendeSaker: List<SakInfo>,
        måned: Måned,
    ): List<SjekkPlan> {
        return løpendeSaker.mapNotNull { sak ->
            val gjeldendeVedtaksdata = sakService.hentGjeldendeVedtaksdata(sak.sakId, måned).fold(
                ifLeft = {
                    log.warn("Fradragssjekk: Klarte ikke hente gjeldende vedtaksdata for sak {}", sak.sakId)
                    null
                },
                ifRight = { it },
            ) ?: return@mapNotNull null

            lagSjekkplanForSak(
                sak = sak,
                gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                måned = måned,
            )
        }
    }

    private fun lagSjekkplanForSak(
        sak: SakInfo,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        måned: Måned,
    ): SjekkPlan? {
        val sjekkpunkter = mutableListOf<Sjekkpunkt>()
        sjekkpunkter += sjekkpunkterForBruker(sak.type, sak.fnr, gjeldendeVedtaksdata, måned)

        val bosituasjonForMåned = gjeldendeVedtaksdata.grunnlagsdata.bosituasjonSomFullstendig()
            .singleOrNull { it.periode.inneholder(måned) }

        when (bosituasjonForMåned) {
            null,
            is Bosituasjon.Fullstendig.Enslig,
            is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen,
            -> Unit

            is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> {
                sjekkpunkter += sjekkpunkterForEps(
                    sakstype = sak.type,
                    epsFnr = bosituasjonForMåned.fnr,
                    epsKategori = EpsKategori.SEKSTISYV_ELLER_ELDRE,
                    gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                    måned = måned,
                )
            }

            is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning,
            is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning,
            -> {
                sjekkpunkter += sjekkpunkterForEps(
                    sakstype = sak.type,
                    epsFnr = bosituasjonForMåned.fnr,
                    epsKategori = EpsKategori.UNDER_SEKSTISYV,
                    gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                    måned = måned,
                )
            }
        }

        return if (sjekkpunkter.isEmpty()) {
            null
        } else {
            SjekkPlan(sak = sak, sjekkpunkter = sjekkpunkter)
        }
    }

    private fun sjekkpunkterForBruker(
        sakstype: Sakstype,
        fnr: Fnr,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        måned: Måned,
    ): List<Sjekkpunkt> {
        return when (sakstype) {
            Sakstype.UFØRE -> listOf(
                Sjekkpunkt(
                    fnr = fnr,
                    tilhører = FradragTilhører.BRUKER,
                    fradragstype = Fradragstype.Uføretrygd,
                    kilde = EksternKilde.PESYS_UFORE,
                    lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Uføretrygd, FradragTilhører.BRUKER, måned),
                ),
                Sjekkpunkt(
                    fnr = fnr,
                    tilhører = FradragTilhører.BRUKER,
                    fradragstype = Fradragstype.Arbeidsavklaringspenger,
                    kilde = EksternKilde.AAP,
                    lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Arbeidsavklaringspenger, FradragTilhører.BRUKER, måned),
                ),
            )

            Sakstype.ALDER -> listOf(
                Sjekkpunkt(
                    fnr = fnr,
                    tilhører = FradragTilhører.BRUKER,
                    fradragstype = Fradragstype.Alderspensjon,
                    kilde = EksternKilde.PESYS_ALDER,
                    lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Alderspensjon, FradragTilhører.BRUKER, måned),
                ),
            )
        }
    }

    private fun sjekkpunkterForEps(
        sakstype: Sakstype,
        epsFnr: Fnr,
        epsKategori: EpsKategori,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        måned: Måned,
    ): List<Sjekkpunkt> {
        return when (sakstype) {
            Sakstype.UFØRE -> when (epsKategori) {
                EpsKategori.UNDER_SEKSTISYV -> listOf(
                    Sjekkpunkt(
                        fnr = epsFnr,
                        tilhører = FradragTilhører.EPS,
                        fradragstype = Fradragstype.Uføretrygd,
                        kilde = EksternKilde.PESYS_UFORE,
                        lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Uføretrygd, FradragTilhører.EPS, måned),
                    ),
                    Sjekkpunkt(
                        fnr = epsFnr,
                        tilhører = FradragTilhører.EPS,
                        fradragstype = Fradragstype.Arbeidsavklaringspenger,
                        kilde = EksternKilde.AAP,
                        lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Arbeidsavklaringspenger, FradragTilhører.EPS, måned),
                    ),
                )

                EpsKategori.SEKSTISYV_ELLER_ELDRE -> listOf(
                    Sjekkpunkt(
                        fnr = epsFnr,
                        tilhører = FradragTilhører.EPS,
                        fradragstype = Fradragstype.Alderspensjon,
                        kilde = EksternKilde.PESYS_ALDER,
                        lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Alderspensjon, FradragTilhører.EPS, måned),
                    ),
                )
            }

            Sakstype.ALDER -> when (epsKategori) {
                EpsKategori.UNDER_SEKSTISYV -> listOf(
                    Sjekkpunkt(
                        fnr = epsFnr,
                        tilhører = FradragTilhører.EPS,
                        fradragstype = Fradragstype.Uføretrygd,
                        kilde = EksternKilde.PESYS_UFORE,
                        lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Uføretrygd, FradragTilhører.EPS, måned),
                    ),
                    Sjekkpunkt(
                        fnr = epsFnr,
                        tilhører = FradragTilhører.EPS,
                        fradragstype = Fradragstype.Arbeidsavklaringspenger,
                        kilde = EksternKilde.AAP,
                        lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Arbeidsavklaringspenger, FradragTilhører.EPS, måned),
                    ),
                )

                EpsKategori.SEKSTISYV_ELLER_ELDRE -> listOf(
                    Sjekkpunkt(
                        fnr = epsFnr,
                        tilhører = FradragTilhører.EPS,
                        fradragstype = Fradragstype.Alderspensjon,
                        kilde = EksternKilde.PESYS_ALDER,
                        lokaltBeløp = gjeldendeVedtaksdata.lokaltFradragsbeløp(Fradragstype.Alderspensjon, FradragTilhører.EPS, måned),
                    ),
                )
            }
        }
    }

    private fun prosesserSjekkplanBatch(
        sjekkplaner: List<SjekkPlan>,
        måned: Måned,
    ): FradragssjekkResultat {
        if (sjekkplaner.isEmpty()) return FradragssjekkResultat()

        val eksterneOppslag = hentEksterneOppslag(sjekkplaner, måned)
        var resultat = FradragssjekkResultat(vurderteSaker = sjekkplaner.size)

        sjekkplaner.forEach { sjekkplan ->
            if (sjekkplan.sjekkpunkter.any { eksterneOppslag.hentOppslag(it) is EksterntOppslag.Feil }) {
                resultat = resultat.registrerHoppetOverPåGrunnAvEksternFeil()
                return@forEach
            }

            when (val avviksvurdering = finnAvvikForSak(sjekkplan, eksterneOppslag)) {
                Avviksvurdering.IngenDiff -> Unit
                is Avviksvurdering.Diff -> {
                    resultat = resultat.registrerSakMedAvvik()
                    when (val oppgaveResultat = opprettOppgaveForFradrag(sjekkplan.sak, måned, avviksvurdering.avvik)) {
                        OppgaveopprettelseResultat.Opprettet -> {
                            resultat = resultat.registrerOpprettetOppgave()
                        }

                        is OppgaveopprettelseResultat.Feilet -> {
                            resultat = resultat.registrerMislykketOppgaveopprettelse(oppgaveResultat.feil)
                        }
                    }
                }
            }
        }

        return resultat
    }

    private fun hentEksterneOppslag(
        sjekkplaner: List<SjekkPlan>,
        måned: Måned,
    ): EksterneOppslag {
        val alleSjekkpunkter = sjekkplaner.flatMap { it.sjekkpunkter }

        val aapFnr = alleSjekkpunkter
            .filter { it.kilde == EksternKilde.AAP }
            .map { it.fnr }
            .distinct()

        val pesysAlderFnr = alleSjekkpunkter
            .filter { it.kilde == EksternKilde.PESYS_ALDER }
            .map { it.fnr }
            .distinct()

        val pesysUføreFnr = alleSjekkpunkter
            .filter { it.kilde == EksternKilde.PESYS_UFORE }
            .map { it.fnr }
            .distinct()

        return EksterneOppslag(
            aap = hentAapOppslag(aapFnr, måned),
            pesysAlder = hentPesysAlderOppslag(pesysAlderFnr, måned.fraOgMed),
            pesysUføre = hentPesysUføreOppslag(pesysUføreFnr, måned.fraOgMed),
        )
    }

    private fun hentPesysAlderOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        return pesysKlient.hentVedtakForPersonPaaDatoAlder(fnr, dato).fold(
            ifLeft = {
                log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", EksternKilde.PESYS_ALDER, fnr.size)
                lagFeilResultat(fnr, "Eksternt kall mot ${EksternKilde.PESYS_ALDER} feilet")
            },
            ifRight = {
                mapPesysOppslag(fnr = fnr, dato = dato, perioderForPerson = it.resultat)
            },
        )
    }

    private fun hentPesysUføreOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        return pesysKlient.hentVedtakForPersonPaaDatoUføre(fnr, dato).fold(
            ifLeft = {
                log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", EksternKilde.PESYS_UFORE, fnr.size)
                lagFeilResultat(fnr, "Eksternt kall mot ${EksternKilde.PESYS_UFORE} feilet")
            },
            ifRight = {
                mapPesysOppslag(fnr = fnr, dato = dato, perioderForPerson = it.resultat)
            },
        )
    }

    private fun mapPesysOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
        perioderForPerson: List<PesysPerioderForPerson>,
    ): Map<Fnr, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()

        val defaultResultat: MutableMap<Fnr, EksterntOppslag> = fnr.associateWith {
            EksterntOppslag.IngenTreff
        }.toMutableMap()

        perioderForPerson.forEach { person ->
            val personFnr = Fnr(person.fnr)
            defaultResultat[personFnr] = person.gyldigPå(dato).fold(
                ifLeft = {
                    log.warn("Fradragssjekk: Ugyldig pesys-respons for {}: {}", personFnr, it)
                    EksterntOppslag.Feil(it)
                },
                ifRight = { periode ->
                    periode?.let { EksterntOppslag.Funnet(it.netto.toDouble()) } ?: EksterntOppslag.IngenTreff
                },
            )
        }

        return defaultResultat
    }

    private fun lagFeilResultat(
        fnr: List<Fnr>,
        feilmelding: String,
    ): Map<Fnr, EksterntOppslag> = fnr.associateWith { EksterntOppslag.Feil(feilmelding) }

    private fun hentAapOppslag(
        fnr: List<Fnr>,
        måned: Måned,
    ): Map<Fnr, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()

        val correlationId = getOrCreateCorrelationIdFromThreadLocal().toString()

        return runBlocking {
            fnr.chunked(AAP_PARALLELLE_OPPSLAG)
                .flatMap { fnrChunk ->
                    fnrChunk.map { personFnr ->
                        async(Dispatchers.IO) {
                            withMdcCorrelationId(correlationId) {
                                personFnr to hentAapOppslagForFnr(personFnr, måned)
                            }
                        }
                    }.awaitAll()
                }
                .toMap()
        }
    }

    private fun hentAapOppslagForFnr(
        fnr: Fnr,
        måned: Måned,
    ): EksterntOppslag {
        return aapKlient.hentMaksimum(
            fnr = fnr,
            fraOgMedDato = måned.fraOgMed,
            tilOgMedDato = måned.tilOgMed,
        ).fold(
            ifLeft = {
                log.warn("Fradragssjekk: AAP-oppslag feilet for fnr {}", fnr)
                EksterntOppslag.Feil("AAP-oppslag feilet")
            },
            ifRight = { response ->
                response.vedtak.gyldigPå(måned.fraOgMed).fold(
                    ifLeft = {
                        log.warn("Fradragssjekk: Ugyldig AAP-respons for fnr {}: {}", fnr, it)
                        EksterntOppslag.Feil(it)
                    },
                    ifRight = { vedtak ->
                        vedtak?.let { EksterntOppslag.Funnet(it.tilMånedsbeløpForSu().toDouble()) }
                            ?: EksterntOppslag.IngenTreff
                    },
                )
            },
        )
    }

    private fun finnAvvikForSak(
        sjekkplan: SjekkPlan,
        eksterneOppslag: EksterneOppslag,
    ): Avviksvurdering {
        val avvik = sjekkplan.sjekkpunkter.mapNotNull { sjekkpunkt ->
            when (val oppslag = eksterneOppslag.hentOppslag(sjekkpunkt)) {
                is EksterntOppslag.Funnet -> when (val lokaltBeløp = sjekkpunkt.lokaltBeløp) {
                    null -> Fradragsavvik(
                        kode = OppgaveConfig.Fradragssjekk.AvvikKode.EKSTERNT_FRADRAG_MANGLER_LOKALT,
                        oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(oppslag.beløp)}, men mangler fradrag på saken.",
                    )
                    else -> if (erSammeBeløp(lokaltBeløp, oppslag.beløp)) {
                        null
                    } else {
                        Fradragsavvik(
                            kode = OppgaveConfig.Fradragssjekk.AvvikKode.ULIKT_BELOP,
                            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} med ulikt beløp. Lokalt=${formatBeløp(lokaltBeløp)}, eksternt=${formatBeløp(oppslag.beløp)} fra ${sjekkpunkt.kilde.kildeNavn}.",
                        )
                    }
                }

                EksterntOppslag.IngenTreff -> sjekkpunkt.lokaltBeløp?.let {
                    Fradragsavvik(
                        kode = OppgaveConfig.Fradragssjekk.AvvikKode.LOKALT_FRADRAG_MANGLER_EKSTERNT,
                        oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} lokalt med beløp ${formatBeløp(it)}, men det finnes ikke i ${sjekkpunkt.kilde.kildeNavn}.",
                    )
                }

                is EksterntOppslag.Feil,
                null,
                -> null
            }
        }.distinctBy { it.kode to it.oppgavetekst }

        return if (avvik.isEmpty()) {
            Avviksvurdering.IngenDiff
        } else {
            Avviksvurdering.Diff(avvik)
        }
    }

    private fun opprettOppgaveForFradrag(
        sak: SakInfo,
        måned: Måned,
        avvik: List<Fradragsavvik>,
    ): OppgaveopprettelseResultat {
        return oppgaveService.opprettOppgaveMedSystembruker(
            OppgaveConfig.Fradragssjekk(
                saksnummer = sak.saksnummer,
                måned = måned,
                avvik = avvik.map {
                    OppgaveConfig.Fradragssjekk.Avvik(
                        kode = it.kode,
                        tekst = it.oppgavetekst,
                    )
                },
                sakstype = sak.type,
                fnr = sak.fnr,
                clock = clock,
            ),
        ).fold(
            ifLeft = {
                OppgaveopprettelseResultat.Feilet(
                    MislykketOppgaveopprettelse(
                        sakId = sak.sakId,
                        avvikskoder = avvik.map { it.kode }.distinct(),
                    ),
                )
            },
            ifRight = {
                log.info("Fradragssjekk: Opprettet oppgave {} for sak {}", it.oppgaveId, sak.sakId)
                OppgaveopprettelseResultat.Opprettet
            },
        )
    }
}

private data class SjekkPlan(
    val sak: SakInfo,
    val sjekkpunkter: List<Sjekkpunkt>,
)

private data class Sjekkpunkt(
    val fnr: Fnr,
    val tilhører: FradragTilhører,
    val fradragstype: Fradragstype,
    val kilde: EksternKilde,
    val lokaltBeløp: Double?,
)

private enum class EpsKategori {
    UNDER_SEKSTISYV,
    SEKSTISYV_ELLER_ELDRE,
}

private enum class EksternKilde(val kildeNavn: String) {
    AAP("AAP"),
    PESYS_ALDER("Pesys alder"),
    PESYS_UFORE("Pesys uføre"),
}

private sealed interface EksterntOppslag {
    data class Funnet(val beløp: Double) : EksterntOppslag
    data object IngenTreff : EksterntOppslag
    data class Feil(val grunn: String) : EksterntOppslag
}

private data class EksterneOppslag(
    val aap: Map<Fnr, EksterntOppslag>,
    val pesysAlder: Map<Fnr, EksterntOppslag>,
    val pesysUføre: Map<Fnr, EksterntOppslag>,
) {
    fun hentOppslag(sjekkpunkt: Sjekkpunkt): EksterntOppslag? {
        return when (sjekkpunkt.kilde) {
            EksternKilde.AAP -> aap[sjekkpunkt.fnr]
            EksternKilde.PESYS_ALDER -> pesysAlder[sjekkpunkt.fnr]
            EksternKilde.PESYS_UFORE -> pesysUføre[sjekkpunkt.fnr]
        }
    }
}

private data class FradragssjekkResultat(
    val vurderteSaker: Int = 0,
    val sakerMedAvvik: Int = 0,
    val opprettedeOppgaver: Int = 0,
    val hoppetOverPåGrunnAvEksternFeil: Int = 0,
    val mislykkedeOppgaveopprettelser: List<MislykketOppgaveopprettelse> = emptyList(),
) {
    operator fun plus(other: FradragssjekkResultat): FradragssjekkResultat {
        return FradragssjekkResultat(
            vurderteSaker = vurderteSaker + other.vurderteSaker,
            sakerMedAvvik = sakerMedAvvik + other.sakerMedAvvik,
            opprettedeOppgaver = opprettedeOppgaver + other.opprettedeOppgaver,
            hoppetOverPåGrunnAvEksternFeil = hoppetOverPåGrunnAvEksternFeil + other.hoppetOverPåGrunnAvEksternFeil,
            mislykkedeOppgaveopprettelser = mislykkedeOppgaveopprettelser + other.mislykkedeOppgaveopprettelser,
        )
    }

    fun registrerSakMedAvvik(): FradragssjekkResultat = copy(sakerMedAvvik = sakerMedAvvik + 1)

    fun registrerOpprettetOppgave(): FradragssjekkResultat = copy(opprettedeOppgaver = opprettedeOppgaver + 1)

    fun registrerHoppetOverPåGrunnAvEksternFeil(): FradragssjekkResultat {
        return copy(hoppetOverPåGrunnAvEksternFeil = hoppetOverPåGrunnAvEksternFeil + 1)
    }

    fun registrerMislykketOppgaveopprettelse(
        feil: MislykketOppgaveopprettelse,
    ): FradragssjekkResultat {
        return copy(mislykkedeOppgaveopprettelser = mislykkedeOppgaveopprettelser + feil)
    }
}

private sealed interface Avviksvurdering {
    data object IngenDiff : Avviksvurdering
    data class Diff(val avvik: List<Fradragsavvik>) : Avviksvurdering
}

private data class Fradragsavvik(
    val kode: OppgaveConfig.Fradragssjekk.AvvikKode,
    val oppgavetekst: String,
)

private data class MislykketOppgaveopprettelse(
    val sakId: UUID,
    val avvikskoder: List<OppgaveConfig.Fradragssjekk.AvvikKode>,
)

private sealed interface OppgaveopprettelseResultat {
    data object Opprettet : OppgaveopprettelseResultat
    data class Feilet(val feil: MislykketOppgaveopprettelse) : OppgaveopprettelseResultat
}

private inline fun <T> withMdcCorrelationId(
    correlationId: String,
    block: () -> T,
): T {
    val previousCorrelationId = MDC.get(CORRELATION_ID_HEADER)

    return try {
        MDC.put(CORRELATION_ID_HEADER, correlationId)
        block()
    } finally {
        if (previousCorrelationId == null) {
            MDC.remove(CORRELATION_ID_HEADER)
        } else {
            MDC.put(CORRELATION_ID_HEADER, previousCorrelationId)
        }
    }
}

private fun GjeldendeVedtaksdata.lokaltFradragsbeløp(
    fradragstype: Fradragstype,
    tilhører: FradragTilhører,
    måned: Måned,
): Double? {
    val relevanteFradrag = grunnlagsdata.fradragsgrunnlag.filter {
        it.fradragstype == fradragstype &&
            it.tilhører == tilhører &&
            it.periode.inneholder(måned)
    }

    return relevanteFradrag.takeIf { it.isNotEmpty() }?.sumOf { it.månedsbeløp }
}

private fun Sjekkpunkt.brukerType(): String = when (tilhører) {
    FradragTilhører.BRUKER -> "Bruker"
    FradragTilhører.EPS -> "EPS"
}

private fun List<PesysPeriode>.gyldigPå(dato: LocalDate): arrow.core.Either<String, PesysPeriode?> {
    val gyldigePerioder = filter {
        !dato.isBefore(it.fom) && (it.tom == null || !dato.isAfter(it.tom))
    }

    return when (gyldigePerioder.size) {
        0 -> arrow.core.Either.Right(null)
        1 -> arrow.core.Either.Right(gyldigePerioder.single())
        else -> arrow.core.Either.Left("Fant flere gyldige perioder på dato $dato")
    }
}

private fun PesysPerioderForPerson.gyldigPå(dato: LocalDate): arrow.core.Either<String, PesysPeriode?> {
    return perioder.gyldigPå(dato)
}

private fun List<MaksimumVedtakDto>.gyldigPå(dato: LocalDate): arrow.core.Either<String, MaksimumVedtakDto?> {
    val gyldigeVedtak = filter { vedtak ->
        val periode = vedtak.periode ?: return@filter false
        val fraOgMed = periode.fraOgMedDato ?: return@filter false
        val tilOgMed = periode.tilOgMedDato ?: return@filter false
        vedtak.opphorsAarsak == null && !dato.isBefore(fraOgMed) && !dato.isAfter(tilOgMed)
    }

    return when (gyldigeVedtak.size) {
        0 -> arrow.core.Either.Right(null)
        1 -> arrow.core.Either.Right(gyldigeVedtak.single())
        else -> arrow.core.Either.Left("Fant flere gyldige AAP-vedtak på dato $dato")
    }
}

private fun MaksimumVedtakDto.tilMånedsbeløpForSu(): BigDecimal {
    val dagsats = requireNotNull(dagsats) { "Kan ikke beregne AAP-beløp uten dagsats" }
    return BigDecimal(dagsats)
        .multiply(AAP_STONADSDAGER_PER_AR)
        .divide(AAP_MANEDER_PER_AR, 2, RoundingMode.HALF_UP)
}

/**
 * Bryr oss kun om 10% diff?
 */
private fun erSammeBeløp(lokaltBeløp: Double, eksterntBeløp: Double): Boolean {
    return kotlin.math.abs(lokaltBeløp - eksterntBeløp) > BELOPS_TOLERANSE
}

private fun formatBeløp(beløp: Double): String {
    return BigDecimal.valueOf(beløp).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
