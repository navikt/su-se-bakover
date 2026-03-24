package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumVedtakDto
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPeriode
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.inneholder
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.LoggerFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

interface FradragsjobbenService {
    fun sjekkLøpendeSakerForFradragIEksterneSystemer()
}

private const val INTERN_SAK_BATCH_STORRELSE = 200
private const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50
private const val BELOPS_TOLERANSE = 0.01
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
        val sjekkplanBuffer = mutableListOf<SjekkPlan>()
        var resultat = FradragssjekkResultat()

        hentAlleSaker()
            .chunked(INTERN_SAK_BATCH_STORRELSE)
            .forEach { sakerPerBatch ->
                val løpendeSaker = filtrerSakerMedLøpendeUtbetalingForMåned(sakerPerBatch, måned)
                val sjekkplaner = lagSjekkplanerForLøpendeSaker(løpendeSaker, måned)
                sjekkplanBuffer.addAll(sjekkplaner)

                while (sjekkplanBuffer.size >= EKSTERN_OPPSLAG_BATCH_STORRELSE) {
                    val batch = sjekkplanBuffer.take(EKSTERN_OPPSLAG_BATCH_STORRELSE)
                    resultat = resultat + prosesserSjekkplanBatch(batch, måned)
                    sjekkplanBuffer.subList(0, EKSTERN_OPPSLAG_BATCH_STORRELSE).clear()
                }
            }

        if (sjekkplanBuffer.isNotEmpty()) {
            resultat += prosesserSjekkplanBatch(sjekkplanBuffer.toList(), måned)
        }

        log.info(
            "Fradragssjekk fullført for måned {}. Vurderte saker: {}, saker med avvik: {}, opprettede oppgaver: {}, hoppet over pga eksterne feil: {}",
            måned,
            resultat.vurderteSaker,
            resultat.sakerMedAvvik,
            resultat.opprettedeOppgaver,
            resultat.hoppetOverPåGrunnAvEksternFeil,
        )
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

            lagSjekkplan(
                sak = sak,
                gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                måned = måned,
            )
        }
    }

    private fun lagSjekkplan(
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
            if (sjekkplan.sjekkpunkter.any { eksterneOppslag[EksternOppslagNøkkel(it.fnr, it.kilde)] is EksterntOppslag.Feil }) {
                resultat += FradragssjekkResultat(hoppetOverPåGrunnAvEksternFeil = 1)
                return@forEach
            }

            val avvik = finnAvvikForSak(sjekkplan, eksterneOppslag)
            if (avvik.isNotEmpty()) {
                resultat += FradragssjekkResultat(sakerMedAvvik = 1)
                opprettOppgaveForFradrag(sjekkplan.sak, måned, avvik)
                    ?.let { resultat += FradragssjekkResultat(opprettedeOppgaver = 1) }
            }
        }

        return resultat
    }

    private fun hentEksterneOppslag(
        sjekkplaner: List<SjekkPlan>,
        måned: Måned,
    ): Map<EksternOppslagNøkkel, EksterntOppslag> {
        val resultat = mutableMapOf<EksternOppslagNøkkel, EksterntOppslag>()
        val sjekkpunkter = sjekkplaner.flatMap { it.sjekkpunkter }

        val aapFnr = sjekkpunkter.filter { it.kilde == EksternKilde.AAP }.map { it.fnr }.distinct()
        aapFnr.forEach { fnr ->
            val nøkkel = EksternOppslagNøkkel(fnr, EksternKilde.AAP)
            resultat[nøkkel] = hentAapOppslag(fnr, måned)
        }

        val pesysAlderFnr = sjekkpunkter.filter { it.kilde == EksternKilde.PESYS_ALDER }.map { it.fnr }.distinct()
        resultat += hentPesysAlderOppslag(pesysAlderFnr, måned.fraOgMed)

        val pesysUføreFnr = sjekkpunkter.filter { it.kilde == EksternKilde.PESYS_UFORE }.map { it.fnr }.distinct()
        resultat += hentPesysUføreOppslag(pesysUføreFnr, måned.fraOgMed)

        return resultat
    }

    private fun hentPesysAlderOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<EksternOppslagNøkkel, EksterntOppslag> {
        return pesysKlient.hentVedtakForPersonPaaDatoAlder(fnr, dato).fold(
            ifLeft = {
                log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", EksternKilde.PESYS_ALDER, fnr.size)
                lagFeilResultat(fnr, EksternKilde.PESYS_ALDER, "Eksternt kall mot ${EksternKilde.PESYS_ALDER} feilet")
            },
            ifRight = {
                mapPesysOppslag(
                    fnr = fnr,
                    kilde = EksternKilde.PESYS_ALDER,
                    dato = dato,
                    perioderForPerson = it.resultat,
                )
            },
        )
    }

    private fun hentPesysUføreOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<EksternOppslagNøkkel, EksterntOppslag> {
        return pesysKlient.hentVedtakForPersonPaaDatoUføre(fnr, dato).fold(
            ifLeft = {
                log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", EksternKilde.PESYS_UFORE, fnr.size)
                lagFeilResultat(fnr, EksternKilde.PESYS_UFORE, "Eksternt kall mot ${EksternKilde.PESYS_UFORE} feilet")
            },
            ifRight = {
                mapPesysOppslag(
                    fnr = fnr,
                    kilde = EksternKilde.PESYS_UFORE,
                    dato = dato,
                    perioderForPerson = it.resultat,
                )
            },
        )
    }

    private fun mapPesysOppslag(
        fnr: List<Fnr>,
        kilde: EksternKilde,
        dato: LocalDate,
        perioderForPerson: List<PesysPerioderForPerson>,
    ): Map<EksternOppslagNøkkel, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()

        val defaultResultat: MutableMap<EksternOppslagNøkkel, EksterntOppslag> = fnr.associate {
            EksternOppslagNøkkel(it, kilde) to EksterntOppslag.IngenTreff
        }.toMutableMap()

        perioderForPerson.forEach { person ->
            val nøkkel = EksternOppslagNøkkel(Fnr(person.fnr), kilde)
            defaultResultat[nøkkel] = person.gyldigPå(dato).fold(
                ifLeft = {
                    log.warn("Fradragssjekk: Ugyldig pesys-respons for {} på {}: {}", nøkkel.fnr, kilde, it)
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
        kilde: EksternKilde,
        feilmelding: String,
    ): Map<EksternOppslagNøkkel, EksterntOppslag> {
        return fnr.associate {
            EksternOppslagNøkkel(it, kilde) to EksterntOppslag.Feil(feilmelding)
        }
    }

    private fun hentAapOppslag(
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
        eksterneOppslag: Map<EksternOppslagNøkkel, EksterntOppslag>,
    ): List<String> {
        return sjekkplan.sjekkpunkter.mapNotNull { sjekkpunkt ->
            when (val oppslag = eksterneOppslag[EksternOppslagNøkkel(sjekkpunkt.fnr, sjekkpunkt.kilde)]) {
                is EksterntOppslag.Funnet -> when (val lokaltBeløp = sjekkpunkt.lokaltBeløp) {
                    null -> "${sjekkpunkt.tilhørerNavn()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(oppslag.beløp)}, men mangler fradrag på saken."
                    else -> if (erSammeBeløp(lokaltBeløp, oppslag.beløp)) {
                        null
                    } else {
                        "${sjekkpunkt.tilhørerNavn()} har ${sjekkpunkt.fradragstype} med ulikt beløp. Lokalt=${formatBeløp(lokaltBeløp)}, eksternt=${formatBeløp(oppslag.beløp)} fra ${sjekkpunkt.kilde.kildeNavn}."
                    }
                }

                EksterntOppslag.IngenTreff -> sjekkpunkt.lokaltBeløp?.let {
                    "${sjekkpunkt.tilhørerNavn()} har ${sjekkpunkt.fradragstype} lokalt med beløp ${formatBeløp(it)}, men det finnes ikke i ${sjekkpunkt.kilde.kildeNavn}."
                }

                is EksterntOppslag.Feil,
                null,
                -> null
            }
        }.distinct()
    }

    private fun opprettOppgaveForFradrag(
        sak: SakInfo,
        måned: Måned,
        avvik: List<String>,
    ): no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse? {
        return oppgaveService.opprettOppgaveMedSystembruker(
            OppgaveConfig.Fradragssjekk(
                saksnummer = sak.saksnummer,
                måned = måned,
                avvik = avvik,
                sakstype = sak.type,
                fnr = sak.fnr,
                clock = clock,
            ),
        ).fold(
            ifLeft = {
                log.error("Fradragssjekk: Klarte ikke opprette oppgave for sak {}", sak.sakId)
                null
            },
            ifRight = {
                log.info("Fradragssjekk: Opprettet oppgave {} for sak {}", it.oppgaveId, sak.sakId)
                it
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

private data class EksternOppslagNøkkel(
    val fnr: Fnr,
    val kilde: EksternKilde,
)

private sealed interface EksterntOppslag {
    data class Funnet(val beløp: Double) : EksterntOppslag
    data object IngenTreff : EksterntOppslag
    data class Feil(val grunn: String) : EksterntOppslag
}

private data class FradragssjekkResultat(
    val vurderteSaker: Int = 0,
    val sakerMedAvvik: Int = 0,
    val opprettedeOppgaver: Int = 0,
    val hoppetOverPåGrunnAvEksternFeil: Int = 0,
) {
    operator fun plus(other: FradragssjekkResultat): FradragssjekkResultat {
        return FradragssjekkResultat(
            vurderteSaker = vurderteSaker + other.vurderteSaker,
            sakerMedAvvik = sakerMedAvvik + other.sakerMedAvvik,
            opprettedeOppgaver = opprettedeOppgaver + other.opprettedeOppgaver,
            hoppetOverPåGrunnAvEksternFeil = hoppetOverPåGrunnAvEksternFeil + other.hoppetOverPåGrunnAvEksternFeil,
        )
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

private fun Sjekkpunkt.tilhørerNavn(): String = when (tilhører) {
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

private fun erSammeBeløp(lokaltBeløp: Double, eksterntBeløp: Double): Boolean {
    return kotlin.math.abs(lokaltBeløp - eksterntBeløp) < BELOPS_TOLERANSE
}

private fun formatBeløp(beløp: Double): String {
    return BigDecimal.valueOf(beløp).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
