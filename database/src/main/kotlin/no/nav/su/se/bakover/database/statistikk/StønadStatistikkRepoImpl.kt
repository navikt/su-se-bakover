package no.nav.su.se.bakover.database.statistikk

import kotliquery.Row
import no.nav.su.se.bakover.common.domain.JaNei
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkRepo
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkDto.Fradrag
import statistikk.domain.StønadstatistikkDto.Månedsbeløp
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth
import java.util.UUID

// TODO: ikke i bruk
class StønadStatistikkRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : StønadStatistikkRepo {

    override fun lagreMånedStatistikk(månedStatistikk: StønadstatistikkMåned) {
        return dbMetrics.timeQuery("hentStatistikkForMåned") {
            sessionFactory.withSession { session ->
                lagreMånedStatistikk(session, månedStatistikk)
            }
        }
    }

    private fun lagreMånedStatistikk(session: Session, månedStatistikk: StønadstatistikkMåned) {
        """
            INSERT INTO stoenad_maaned_statistikk (
                id, maaned, funksjonell_tid, teknisk_tid, sak_id, stonadstype, personnummer, personnummer_eps,
                vedtakstype, vedtaksresultat, vedtaksdato, vedtak_fra_og_med, vedtak_til_og_med,
                opphorsgrunn, opphorsdato, behandlende_enhet_kode, aarsakStans,
                stonadsklassifisering, sats, utbetales, fradragSum, uforegrad, fribeloepEps, alderspensjon, alderspensjonEps,
                arbeidsavklaringspenger, arbeidsavklaringspengerEps, arbeidsinntekt, arbeidsinntektEps,
                omstillingsstonad, omstillingsstonadEps, avtalefestetPensjon, avtalefestetPensjonEps,
                avtalefestetPensjonPrivat, avtalefestetPensjonPrivatEps, bidragEtterEkteskapsloven,
                bidragEtterEkteskapslovenEps, dagpenger, dagpengerEps, fosterhjemsgodtgjorelse,
                fosterhjemsgodtgjorelseEps, gjenlevendepensjon, gjenlevendepensjonEps, introduksjonsstonad,
                introduksjonsstonadEps, kapitalinntekt, kapitalinntektEps, kontantstotte, kontantstotteEps,
                kvalifiseringsstonad, kvalifiseringsstonadEps, navYtelserTilLivsopphold, navYtelserTilLivsoppholdEps,
                offentligPensjon, offentligPensjonEps, privatPensjon, privatPensjonEps, sosialstonad, sosialstonadEps,
                statensLaanekasse, statensLaanekasseEps, supplerendeStonad, supplerendeStonadEps, sykepenger,
                sykepengerEps, tiltakspenger, tiltakspengerEps, ventestonad, ventestonadEps, uforetrygd, uforetrygdEps,
                forventetInntekt, forventetInntektEps, avkortingUtenlandsopphold, avkortingUtenlandsoppholdEps,
                underMinstenivaa, underMinstenivaaEps, annet, annetEps
            ) VALUES (
                :id, :maaned, :funksjonell_tid, :teknisk_tid, :sak_id, :stonadstype, :personnummer, :personnummer_eps,
                :vedtakstype, :vedtaksresultat, :vedtaksdato, :vedtak_fra_og_med, :vedtak_til_og_med,
                :opphorsgrunn, :opphorsdato, :behandlende_enhet_kode, :aarsakStans,
                :stonadsklassifisering, :sats, :utbetales, :fradragSum, :uforegrad, :fribeloepEps, :alderspensjon, :alderspensjonEps,
                :arbeidsavklaringspenger, :arbeidsavklaringspengerEps, :arbeidsinntekt, :arbeidsinntektEps,
                :omstillingsstonad, :omstillingsstonadEps, :avtalefestetPensjon, :avtalefestetPensjonEps,
                :avtalefestetPensjonPrivat, :avtalefestetPensjonPrivatEps, :bidragEtterEkteskapsloven,
                :bidragEtterEkteskapslovenEps, :dagpenger, :dagpengerEps, :fosterhjemsgodtgjorelse,
                :fosterhjemsgodtgjorelseEps, :gjenlevendepensjon, :gjenlevendepensjonEps, :introduksjonsstonad,
                :introduksjonsstonadEps, :kapitalinntekt, :kapitalinntektEps, :kontantstotte, :kontantstotteEps,
                :kvalifiseringsstonad, :kvalifiseringsstonadEps, :navYtelserTilLivsopphold, :navYtelserTilLivsoppholdEps,
                :offentligPensjon, :offentligPensjonEps, :privatPensjon, :privatPensjonEps, :sosialstonad, :sosialstonadEps,
                :statensLaanekasse, :statensLaanekasseEps, :supplerendeStonad, :supplerendeStonadEps, :sykepenger,
                :sykepengerEps, :tiltakspenger, :tiltakspengerEps, :ventestonad, :ventestonadEps, :uforetrygd, :uforetrygdEps,
                :forventetInntekt, :forventetInntektEps, :avkortingUtenlandsopphold, :avkortingUtenlandsoppholdEps,
                :underMinstenivaa, :underMinstenivaaEps, :annet, :annetEps
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to månedStatistikk.id,
                    "maaned" to månedStatistikk.måned.atDay(1),
                    "funksjonell_tid" to månedStatistikk.funksjonellTid,
                    "teknisk_tid" to månedStatistikk.tekniskTid,
                    "sak_id" to månedStatistikk.sakId,
                    "stonadstype" to månedStatistikk.stonadstype.name,
                    "personnummer" to månedStatistikk.personnummer.toString(),
                    "personnummer_eps" to månedStatistikk.personNummerEps?.toString(),
                    "vedtakstype" to månedStatistikk.vedtakstype.name,
                    "vedtaksresultat" to månedStatistikk.vedtaksresultat.name,
                    "vedtaksdato" to månedStatistikk.vedtaksdato,
                    "vedtak_fra_og_med" to månedStatistikk.vedtakFraOgMed,
                    "vedtak_til_og_med" to månedStatistikk.vedtakTilOgMed,
                    "opphorsgrunn" to månedStatistikk.opphorsgrunn,
                    "opphorsdato" to månedStatistikk.opphorsdato,
                    "behandlende_enhet_kode" to månedStatistikk.behandlendeEnhetKode,
                    "aarsakStans" to månedStatistikk.årsakStans,
                    "stonadsklassifisering" to månedStatistikk.stonadsklassifisering?.toString(),
                    "sats" to månedStatistikk.sats,
                    "utbetales" to månedStatistikk.utbetales,
                    "fradragSum" to månedStatistikk.fradragSum,
                    "uforegrad" to månedStatistikk.uføregrad,
                    "fribeloepEps" to månedStatistikk.fribeløpEps,
                    "alderspensjon" to månedStatistikk.alderspensjon,
                    "alderspensjonEps" to månedStatistikk.alderspensjonEps,
                    "arbeidsavklaringspenger" to månedStatistikk.arbeidsavklaringspenger,
                    "arbeidsavklaringspengerEps" to månedStatistikk.arbeidsavklaringspengerEps,
                    "arbeidsinntekt" to månedStatistikk.arbeidsinntekt,
                    "arbeidsinntektEps" to månedStatistikk.arbeidsinntektEps,
                    "omstillingsstonad" to månedStatistikk.omstillingsstønad,
                    "omstillingsstonadEps" to månedStatistikk.omstillingsstønadEps,
                    "avtalefestetPensjon" to månedStatistikk.avtalefestetPensjon,
                    "avtalefestetPensjonEps" to månedStatistikk.avtalefestetPensjonEps,
                    "avtalefestetPensjonPrivat" to månedStatistikk.avtalefestetPensjonPrivat,
                    "avtalefestetPensjonPrivatEps" to månedStatistikk.avtalefestetPensjonPrivatEps,
                    "bidragEtterEkteskapsloven" to månedStatistikk.bidragEtterEkteskapsloven,
                    "bidragEtterEkteskapslovenEps" to månedStatistikk.bidragEtterEkteskapslovenEps,
                    "dagpenger" to månedStatistikk.dagpenger,
                    "dagpengerEps" to månedStatistikk.dagpengerEps,
                    "fosterhjemsgodtgjorelse" to månedStatistikk.fosterhjemsgodtgjørelse,
                    "fosterhjemsgodtgjorelseEps" to månedStatistikk.fosterhjemsgodtgjørelseEps,
                    "gjenlevendepensjon" to månedStatistikk.gjenlevendepensjon,
                    "gjenlevendepensjonEps" to månedStatistikk.gjenlevendepensjonEps,
                    "introduksjonsstonad" to månedStatistikk.introduksjonsstønad,
                    "introduksjonsstonadEps" to månedStatistikk.navYtelserTilLivsoppholdEps,
                    "kapitalinntekt" to månedStatistikk.kapitalinntekt,
                    "kapitalinntektEps" to månedStatistikk.kapitalinntektEps,
                    "kontantstotte" to månedStatistikk.kontantstøtte,
                    "kontantstotteEps" to månedStatistikk.kontantstøtteEps,
                    "kvalifiseringsstonad" to månedStatistikk.kvalifiseringsstønad,
                    "kvalifiseringsstonadEps" to månedStatistikk.kapitalinntektEps,
                    "navYtelserTilLivsopphold" to månedStatistikk.navYtelserTilLivsopphold,
                    "navYtelserTilLivsoppholdEps" to månedStatistikk.navYtelserTilLivsoppholdEps,
                    "offentligPensjon" to månedStatistikk.offentligPensjon,
                    "offentligPensjonEps" to månedStatistikk.offentligPensjonEps,
                    "privatPensjon" to månedStatistikk.privatPensjon,
                    "privatPensjonEps" to månedStatistikk.privatPensjonEps,
                    "sosialstonad" to månedStatistikk.sosialstønad,
                    "sosialstonadEps" to månedStatistikk.sosialstønadEps,
                    "statensLaanekasse" to månedStatistikk.statensLånekasse,
                    "statensLaanekasseEps" to månedStatistikk.statensLånekasseEps,
                    "supplerendeStonad" to månedStatistikk.supplerendeStønad,
                    "supplerendeStonadEps" to månedStatistikk.supplerendeStønadEps,
                    "sykepenger" to månedStatistikk.sykepenger,
                    "sykepengerEps" to månedStatistikk.sykepengerEps,
                    "tiltakspenger" to månedStatistikk.tiltakspenger,
                    "tiltakspengerEps" to månedStatistikk.tiltakspengerEps,
                    "ventestonad" to månedStatistikk.ventestønad,
                    "ventestonadEps" to månedStatistikk.ventestønadEps,
                    "uforetrygd" to månedStatistikk.uføretrygd,
                    "uforetrygdEps" to månedStatistikk.uføretrygdEps,
                    "forventetInntekt" to månedStatistikk.forventetInntekt,
                    "forventetInntektEps" to månedStatistikk.forventetInntektEps,
                    "avkortingUtenlandsopphold" to månedStatistikk.avkortingUtenlandsopphold,
                    "avkortingUtenlandsoppholdEps" to månedStatistikk.avkortingUtenlandsoppholdEps,
                    "underMinstenivaa" to månedStatistikk.underMinstenivå,
                    "underMinstenivaaEps" to månedStatistikk.underMinstenivåEps,
                    "annet" to månedStatistikk.annet,
                    "annetEps" to månedStatistikk.annetEps,
                ),
                session = session,
            )
        // månedStatistikk.månedsbeløp?.let { lagreMånedsbeløpMedFradrag(session, månedStatistikk.id, it) }
    }

    private fun lagreMånedsbeløpMedFradrag(session: Session, stoenadStatistikkId: UUID, månedsbeløp: Månedsbeløp) {
        val manedsbelopId = UUID.randomUUID()
        """
                    INSERT INTO manedsbelop_statistikk (
                        id, stoenad_statistikk_id, maaned, stonadsklassifisering, sats, utbetales, fradrag_sum, uforegrad
                    ) VALUES (
                        :id, :stoenad_statistikk_id, :maaned, :stonadsklassifisering, :sats, :utbetales, :fradrag_sum,
                        :uforegrad
                    )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to manedsbelopId,
                    "stoenad_statistikk_id" to stoenadStatistikkId,
                    "maaned" to månedsbeløp.måned,
                    "stonadsklassifisering" to månedsbeløp.stonadsklassifisering.name,
                    "sats" to månedsbeløp.sats,
                    "utbetales" to månedsbeløp.utbetales,
                    "fradrag_sum" to månedsbeløp.fradragSum,
                    "uforegrad" to månedsbeløp.uføregrad,
                ),
                session = session,
            )
        månedsbeløp.fradrag.forEach { fradrag ->
            val fradragId = UUID.randomUUID()
            """
                        INSERT INTO fradrag_statistikk (
                            id, manedsbelop_id, fradragstype, belop, tilhorer, er_utenlandsk
                        ) VALUES (
                            :id, :manedsbelop_id, :fradragstype, :belop, :tilhorer, :er_utenlandsk
                        )
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to fradragId,
                        "manedsbelop_id" to manedsbelopId,
                        "fradragstype" to fradrag.fradragstype,
                        "belop" to fradrag.beløp,
                        "tilhorer" to fradrag.tilhører,
                        "er_utenlandsk" to fradrag.erUtenlandsk,
                    ),
                    session = session,
                )
        }
    }

    override fun hentMånedStatistikk(måned: YearMonth): List<StønadstatistikkMåned> {
        return dbMetrics.timeQuery("hentStønadstatistikkMåned") {
            sessionFactory.withSession { session ->
                """
                SELECT *
                FROM stoenad_maaned_statistikk
                WHERE maaned = :maaned
                """.trimIndent()
                    .hentListe(
                        params = mapOf("maaned" to måned.atDay(1)),
                        session = session,
                    ) { row ->
                        with(row) {
                            val id = uuid("id")
                            StønadstatistikkMåned(
                                id = id,
                                måned = måned,
                                funksjonellTid = tidspunkt("funksjonell_tid"),
                                tekniskTid = tidspunkt("teknisk_tid"),
                                sakId = UUID.fromString(string("sak_id")),
                                stonadstype = StønadstatistikkDto.Stønadstype.valueOf(string("stonadstype")),
                                vedtaksdato = localDate("vedtaksdato"),
                                personnummer = Fnr(string("personnummer")),
                                personNummerEps = stringOrNull("personnummer_eps")?.let { Fnr(it) },
                                vedtakFraOgMed = localDate("vedtak_fra_og_med"),
                                vedtakTilOgMed = localDate("vedtak_til_og_med"),
                                vedtakstype = StønadstatistikkDto.Vedtakstype.valueOf(string("vedtakstype")),
                                vedtaksresultat = StønadstatistikkDto.Vedtaksresultat.valueOf(string("vedtaksresultat")),
                                opphorsgrunn = stringOrNull("opphorsgrunn"),
                                opphorsdato = localDateOrNull("opphorsdato"),
                                behandlendeEnhetKode = string("behandlende_enhet_kode"),
                                stonadsklassifisering = stringOrNull("stonadsklassifisering")?.let {
                                    StønadsklassifiseringDto.valueOf(
                                        it,
                                    )
                                },
                                årsakStans = stringOrNull("aarsakStans"),
                                sats = longOrNull("sats"),
                                utbetales = longOrNull("utbetales"),
                                fradragSum = longOrNull("fradragSum"),
                                uføregrad = intOrNull("uforegrad"),
                                fribeløpEps = longOrNull("fribeloepEps"),
                                alderspensjon = intOrNull("alderspensjon"),
                                alderspensjonEps = intOrNull("alderspensjonEps"),
                                arbeidsavklaringspenger = intOrNull("arbeidsavklaringspenger"),
                                arbeidsavklaringspengerEps = intOrNull("arbeidsavklaringspengerEps"),
                                arbeidsinntekt = intOrNull("arbeidsinntekt"),
                                arbeidsinntektEps = intOrNull("arbeidsinntektEps"),
                                omstillingsstønad = intOrNull("omstillingsstonad"),
                                omstillingsstønadEps = intOrNull("omstillingsstonadEps"),
                                avtalefestetPensjon = intOrNull("avtalefestetPensjon"),
                                avtalefestetPensjonEps = intOrNull("avtalefestetPensjonEps"),
                                avtalefestetPensjonPrivat = intOrNull("avtalefestetPensjonPrivat"),
                                avtalefestetPensjonPrivatEps = intOrNull("avtalefestetPensjonPrivatEps"),
                                bidragEtterEkteskapsloven = intOrNull("bidragEtterEkteskapsloven"),
                                bidragEtterEkteskapslovenEps = intOrNull("bidragEtterEkteskapslovenEps"),
                                dagpenger = intOrNull("dagpenger"),
                                dagpengerEps = intOrNull("dagpengerEps"),
                                fosterhjemsgodtgjørelse = intOrNull("fosterhjemsgodtgjorelse"),
                                fosterhjemsgodtgjørelseEps = intOrNull("fosterhjemsgodtgjorelseEps"),
                                gjenlevendepensjon = intOrNull("gjenlevendepensjon"),
                                gjenlevendepensjonEps = intOrNull("gjenlevendepensjonEps"),
                                introduksjonsstønad = intOrNull("introduksjonsstonad"),
                                introduksjonsstønadEps = intOrNull("introduksjonsstonadEps"),
                                kapitalinntekt = intOrNull("kapitalinntekt"),
                                kapitalinntektEps = intOrNull("kapitalinntektEps"),
                                kontantstøtte = intOrNull("kontantstotte"),
                                kontantstøtteEps = intOrNull("kontantstotteEps"),
                                kvalifiseringsstønad = intOrNull("kvalifiseringsstonad"),
                                kvalifiseringsstønadEps = intOrNull("kvalifiseringsstonadEps"),
                                navYtelserTilLivsopphold = intOrNull("navYtelserTilLivsopphold"),
                                navYtelserTilLivsoppholdEps = intOrNull("navYtelserTilLivsoppholdEps"),
                                offentligPensjon = intOrNull("offentligPensjon"),
                                offentligPensjonEps = intOrNull("offentligPensjonEps"),
                                privatPensjon = intOrNull("privatPensjon"),
                                privatPensjonEps = intOrNull("privatPensjonEps"),
                                sosialstønad = intOrNull("sosialstonad"),
                                sosialstønadEps = intOrNull("sosialstonadEps"),
                                statensLånekasse = intOrNull("statensLaanekasse"),
                                statensLånekasseEps = intOrNull("statensLaanekasseEps"),
                                supplerendeStønad = intOrNull("supplerendeStonad"),
                                supplerendeStønadEps = intOrNull("supplerendeStonadEps"),
                                sykepenger = intOrNull("sykepenger"),
                                sykepengerEps = intOrNull("sykepengerEps"),
                                tiltakspenger = intOrNull("tiltakspenger"),
                                tiltakspengerEps = intOrNull("tiltakspengerEps"),
                                ventestønad = intOrNull("ventestonad"),
                                ventestønadEps = intOrNull("ventestonadEps"),
                                uføretrygd = intOrNull("uforetrygd"),
                                uføretrygdEps = intOrNull("uforetrygdEps"),
                                forventetInntekt = intOrNull("forventetInntekt"),
                                forventetInntektEps = intOrNull("forventetInntektEps"),
                                avkortingUtenlandsopphold = intOrNull("avkortingUtenlandsopphold"),
                                avkortingUtenlandsoppholdEps = intOrNull("avkortingUtenlandsoppholdEps"),
                                underMinstenivå = intOrNull("underMinstenivaa"),
                                underMinstenivåEps = intOrNull("underMinstenivaaEps"),
                                annet = intOrNull("annet"),
                                annetEps = intOrNull("annetEps"),
                            )
                        }
                    }
            }
        }
    }

    private fun hentMånedsbeløp(session: Session, stoenadStatistikkId: UUID): List<Månedsbeløp> {
        return """
        SELECT id, maaned, stonadsklassifisering, sats, utbetales, fradrag_sum, uforegrad 
        FROM manedsbelop_statistikk
        WHERE stoenad_statistikk_id = :stoenad_statistikk_id
        """.trimIndent()
            .hentListe(
                params = mapOf("stoenad_statistikk_id" to stoenadStatistikkId),
                session = session,
            ) { row ->
                val manedsbelopId = UUID.fromString(row.string("id"))
                val maaned = row.string("maaned")
                val stonadsklassifisering = StønadsklassifiseringDto.valueOf(row.string("stonadsklassifisering"))
                val sats = row.long("sats")
                val utbetales = row.long("utbetales")
                val fradragSum = row.long("fradrag_sum")
                val uføregrad = row.intOrNull("uforegrad")

                Månedsbeløp(
                    måned = maaned,
                    stonadsklassifisering = stonadsklassifisering,
                    sats = sats,
                    utbetales = utbetales,
                    fradrag = hentInntekter(session, manedsbelopId),
                    fradragSum = fradragSum,
                    uføregrad = uføregrad,
                    0L, // Skal slettes
                )
            }
    }

    private fun hentInntekter(session: Session, manedsbelop_id: UUID): List<Fradrag> {
        return """
            SELECT fradragstype, belop, tilhorer, er_utenlandsk
            FROM fradrag_statistikk 
            WHERE manedsbelop_id = :manedsbelop_id
        """.trimIndent()
            .hentListe(
                params = mapOf("manedsbelop_id" to manedsbelop_id),
                session = session,
            ) { row ->
                Fradrag(
                    fradragstype = row.string("fradragstype"),
                    beløp = row.long("belop"),
                    tilhører = row.string("tilhorer"),
                    erUtenlandsk = row.boolean("er_utenlandsk"),
                )
            }
    }

    private fun Row.toStønadsstatistikk(månedsbeløp: List<Månedsbeløp>): StønadstatistikkDto {
        val personnummerDto = Fnr(string("personnummer"))
        val personnummerEktefelle = stringOrNull("personnummer_ektefelle")?.let { Fnr(it) }
        val funksjonellTid = tidspunkt("funksjonell_tid")
        val tekniskTid = tidspunkt("teknisk_tid")
        val stonadstype = StønadstatistikkDto.Stønadstype.valueOf(string("stonadstype"))
        val sakId = UUID.fromString(string("sak_id"))
        val vedtaksdato = localDate("vedtaksdato")
        val vedtakstype = StønadstatistikkDto.Vedtakstype.valueOf(string("vedtakstype"))
        val vedtaksresultat = StønadstatistikkDto.Vedtaksresultat.valueOf(string("vedtaksresultat"))
        val behandlendeEnhetKode = string("behandlende_enhet_kode")
        val ytelseVirkningstidspunkt = localDate("ytelse_virkningstidspunkt")
        val gjeldendeStonadVirkningstidspunkt = localDate("gjeldende_stonad_virkningstidspunkt")
        val gjeldendeStonadStopptidspunkt = localDate("gjeldende_stonad_stopptidspunkt")
        val gjeldendeStonadUtbetalingsstart = localDate("gjeldende_stonad_utbetalingsstart")
        val gjeldendeStonadUtbetalingsstopp = localDate("gjeldende_stonad_utbetalingsstopp")
        val opphorsgrunn = stringOrNull("opphorsgrunn")
        val opphorsdato = localDateOrNull("opphorsdato")
        val harUtenlandsOpphold = stringOrNull("har_utenlandsopphold")?.let { JaNei.valueOf(it) }
        val harFamiliegjenforening = stringOrNull("har_familiegjenforening")?.let { JaNei.valueOf(it) }
        val flyktningsstatus = stringOrNull("flyktningsstatus")?.let { JaNei.valueOf(it) }
        val versjon = stringOrNull("versjon")

        return StønadstatistikkDto(
            personnummer = personnummerDto,
            personNummerEktefelle = personnummerEktefelle,
            funksjonellTid = funksjonellTid,
            tekniskTid = tekniskTid,
            stonadstype = stonadstype,
            sakId = sakId,
            vedtaksdato = vedtaksdato,
            vedtakstype = vedtakstype,
            vedtaksresultat = vedtaksresultat,
            behandlendeEnhetKode = behandlendeEnhetKode,
            ytelseVirkningstidspunkt = ytelseVirkningstidspunkt,
            gjeldendeStonadVirkningstidspunkt = gjeldendeStonadVirkningstidspunkt,
            gjeldendeStonadStopptidspunkt = gjeldendeStonadStopptidspunkt,
            gjeldendeStonadUtbetalingsstart = gjeldendeStonadUtbetalingsstart,
            gjeldendeStonadUtbetalingsstopp = gjeldendeStonadUtbetalingsstopp,
            opphorsgrunn = opphorsgrunn,
            opphorsdato = opphorsdato,
            flyktningsstatus = flyktningsstatus,
            harUtenlandsOpphold = harUtenlandsOpphold,
            harFamiliegjenforening = harFamiliegjenforening,
            versjon = versjon,
            månedsbeløp = månedsbeløp,
        )
    }
}
