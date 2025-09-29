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
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.YearMonth
import java.util.UUID

class StønadStatistikkRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : StønadStatistikkRepo {
    override fun lagreStønadStatistikk(dto: StønadstatistikkDto) {
        return dbMetrics.timeQuery("lagreHendelseStønadstatistikkDto") {
            sessionFactory.withSession { session ->
                val stoenadStatistikkId = UUID.randomUUID()
                """
                    INSERT INTO stoenad_statistikk (
                    id, har_utenlandsopphold, har_familiegjenforening, personnummer,
                    personnummer_ektefelle, funksjonell_tid, teknisk_tid, stonadstype, sak_id, vedtaksdato,
                    vedtakstype, vedtaksresultat, behandlende_enhet_kode, ytelse_virkningstidspunkt,
                    gjeldende_stonad_virkningstidspunkt, gjeldende_stonad_stopptidspunkt,
                    gjeldende_stonad_utbetalingsstart, gjeldende_stonad_utbetalingsstopp, opphorsgrunn,
                    opphorsdato, flyktningsstatus, versjon
                    ) VALUES (
                        :id, :har_utenlandsopphold, :har_familiegjenforening, :personnummer,
                        :personnummer_ektefelle, :funksjonell_tid, :teknisk_tid, :stonadstype, :sak_id, :vedtaksdato,
                        :vedtakstype, :vedtaksresultat, :behandlende_enhet_kode, :ytelse_virkningstidspunkt,
                        :gjeldende_stonad_virkningstidspunkt, :gjeldende_stonad_stopptidspunkt,
                        :gjeldende_stonad_utbetalingsstart, :gjeldende_stonad_utbetalingsstopp, :opphorsgrunn,
                        :opphorsdato, :flyktningsstatus, :versjon
                    )
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to stoenadStatistikkId,
                            "personnummer" to dto.personnummer.toString(),
                            "personnummer_ektefelle" to dto.personNummerEktefelle?.toString(),
                            "funksjonell_tid" to dto.funksjonellTid,
                            "teknisk_tid" to dto.tekniskTid,
                            "stonadstype" to dto.stonadstype.name,
                            "sak_id" to dto.sakId,
                            "vedtaksdato" to dto.vedtaksdato,
                            "vedtakstype" to dto.vedtakstype.name,
                            "vedtaksresultat" to dto.vedtaksresultat.name,
                            "behandlende_enhet_kode" to dto.behandlendeEnhetKode,
                            "ytelse_virkningstidspunkt" to dto.ytelseVirkningstidspunkt,
                            "gjeldende_stonad_virkningstidspunkt" to dto.gjeldendeStonadVirkningstidspunkt,
                            "gjeldende_stonad_stopptidspunkt" to dto.gjeldendeStonadStopptidspunkt,
                            "gjeldende_stonad_utbetalingsstart" to dto.gjeldendeStonadUtbetalingsstart,
                            "gjeldende_stonad_utbetalingsstopp" to dto.gjeldendeStonadUtbetalingsstopp,
                            "opphorsgrunn" to dto.opphorsgrunn,
                            "opphorsdato" to dto.opphorsdato,
                            "har_utenlandsopphold" to dto.harUtenlandsOpphold?.name,
                            "har_familiegjenforening" to dto.harFamiliegjenforening?.name,
                            "flyktningsstatus" to dto.flyktningsstatus?.name,
                            "versjon" to dto.versjon,
                        ),
                        session = session,
                    )

                dto.månedsbeløp.forEach {
                    lagreMånedsbeløpMedFradrag(session, stoenadStatistikkId, it)
                }
            }
        }
    }

    override fun hentHendelserForFnr(fnr: Fnr): List<StønadstatistikkDto> {
        return dbMetrics.timeQuery("hentHendelseStønadstatistikkDto") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    id, har_utenlandsopphold, har_familiegjenforening, personnummer,
                    personnummer_ektefelle, funksjonell_tid, teknisk_tid, stonadstype, sak_id, vedtaksdato,
                    vedtakstype, vedtaksresultat, behandlende_enhet_kode, ytelse_virkningstidspunkt,
                    gjeldende_stonad_virkningstidspunkt, gjeldende_stonad_stopptidspunkt,
                    gjeldende_stonad_utbetalingsstart, gjeldende_stonad_utbetalingsstopp, opphorsgrunn,
                    opphorsdato, flyktningsstatus, versjon
                FROM stoenad_statistikk
                WHERE personnummer = :fnr
                """.trimIndent()
                    .hentListe(
                        params = mapOf("fnr" to fnr.toString()),
                        session = session,
                    ) { row ->
                        val månedsbeløp = hentMånedsbeløp(session, row.uuid("id"))
                        row.toStønadsstatistikk(månedsbeløp)
                    }
            }
        }
    }

    /**
     * Finner all nyligste statistikk for stønader som er løpende på gitt måned og lagrer det månedlige "resultatet".
     * Det vil si slik tilstanden til stønaden var ved månedskifte. Det vil også gjelde hvis det ikke har skjedd
     * endringer siden forrige jobb.
     *
     * TODO fjerne??
     *
     */
    override fun hentOgLagreStatistikkForMåned(måned: YearMonth) {
        return dbMetrics.timeQuery("hentStatistikkForMåned") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    id, har_utenlandsopphold, har_familiegjenforening, personnummer,
                    personnummer_ektefelle, funksjonell_tid, teknisk_tid, stonadstype, sak_id, vedtaksdato,
                    vedtakstype, vedtaksresultat, behandlende_enhet_kode, ytelse_virkningstidspunkt,
                    gjeldende_stonad_virkningstidspunkt, gjeldende_stonad_stopptidspunkt,
                    gjeldende_stonad_utbetalingsstart, gjeldende_stonad_utbetalingsstopp, opphorsgrunn,
                    opphorsdato, flyktningsstatus, versjon
                FROM stoenad_statistikk
                WHERE gjeldende_stonad_utbetalingsstart <= :dato
                        AND gjeldende_stonad_utbetalingsstopp >= :dato
                        AND teknisk_tid <= :dato
                """.trimIndent()
                    .hentListe(
                        params = mapOf("dato" to måned.atEndOfMonth()),
                        session = session,
                    ) { row ->
                        val månedsbeløp = hentMånedsbeløp(session, row.uuid("id"))
                        row.toStønadsstatistikk(månedsbeløp)
                    }.groupBy { it.sakId }.map {
                        val nyligste = it.value.maxBy { it.vedtaksdato }
                        val månedsstatistikk = mapMånedstatistikk(måned, nyligste)
                        lagreMånedStatistikk(session, månedsstatistikk)
                    }
            }
        }
    }

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
                opphorsgrunn, opphorsdato, behandlende_enhet_kode
            ) VALUES (
                :id, :maaned, :funksjonell_tid, :teknisk_tid, :sak_id, :stonadstype, :personnummer, :personnummer_eps,
                :vedtakstype, :vedtaksresultat, :vedtaksdato, :vedtak_fra_og_med, :vedtak_til_og_med,
                :opphorsgrunn, :opphorsdato, :behandlende_enhet_kode
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
                                harUtenlandsOpphold = stringOrNull("har_utenlandsopphold")?.let { JaNei.valueOf(it) },
                                harFamiliegjenforening = stringOrNull("har_familiegjenforening")?.let { JaNei.valueOf(it) },
                                flyktningsstatus = stringOrNull("flyktningsstatus")?.let { JaNei.valueOf(it) },
                                stonadsklassifisering = string("stonadsklassifisering").let {
                                    StønadsklassifiseringDto.valueOf(
                                        it,
                                    )
                                },
                                sats = long("sats"),
                                utbetales = long("utbetales"),
                                fradragSum = long("fradragSum"),
                                uføregrad = intOrNull("uføregrad"),
                                alderspensjon = intOrNull("alderspensjon"),
                                alderspensjonEps = intOrNull("alderspensjonEps"),
                                arbeidsavklaringspenger = intOrNull("arbeidsavklaringspenger"),
                                arbeidsavklaringspengerEps = intOrNull("arbeidsavklaringspengerEps"),
                                arbeidsinntekt = intOrNull("arbeidsinntekt"),
                                arbeidsinntektEps = intOrNull("arbeidsinntektEps"),
                                omstillingsstønad = intOrNull("omstillingsstønad"),
                                omstillingsstønadEps = intOrNull("omstillingsstønadEps"),
                                avtalefestetPensjon = intOrNull("avtalefestetPensjon"),
                                avtalefestetPensjonEps = intOrNull("avtalefestetPensjonEps"),
                                avtalefestetPensjonPrivat = intOrNull("avtalefestetPensjonPrivat"),
                                avtalefestetPensjonPrivatEps = intOrNull("avtalefestetPensjonPrivatEps"),
                                bidragEtterEkteskapsloven = intOrNull("bidragEtterEkteskapsloven"),
                                bidragEtterEkteskapslovenEps = intOrNull("bidragEtterEkteskapslovenEps"),
                                dagpenger = intOrNull("dagpenger"),
                                dagpengerEps = intOrNull("dagpengerEps"),
                                fosterhjemsgodtgjørelse = intOrNull("fosterhjemsgodtgjørelse"),
                                fosterhjemsgodtgjørelseEps = intOrNull("fosterhjemsgodtgjørelseEps"),
                                gjenlevendepensjon = intOrNull("gjenlevendepensjon"),
                                gjenlevendepensjonEps = intOrNull("gjenlevendepensjonEps"),
                                introduksjonsstønad = intOrNull("introduksjonsstønad"),
                                introduksjonsstønadEps = intOrNull("introduksjonsstønadEps"),
                                kapitalinntekt = intOrNull("kapitalinntekt"),
                                kapitalinntektEps = intOrNull("kapitalinntektEps "),
                                kontantstøtte = intOrNull("kontantstøtte"),
                                kontantstøtteEps = intOrNull("kontantstøtteEps"),
                                kvalifiseringsstønad = intOrNull("kvalifiseringsstønad"),
                                kvalifiseringsstønadEps = intOrNull("kvalifiseringsstønadEps"),
                                navYtelserTilLivsopphold = intOrNull("navYtelserTilLivsopphold"),
                                navYtelserTilLivsoppholdEps = intOrNull("navYtelserTilLivsoppholdEps"),
                                offentligPensjon = intOrNull("offentligPensjon"),
                                offentligPensjonEps = intOrNull("offentligPensjonEps"),
                                privatPensjon = intOrNull("privatPensjon"),
                                privatPensjonEps = intOrNull("privatPensjonEps"),
                                sosialstønad = intOrNull("sosialstønad"),
                                sosialstønadEps = intOrNull("sosialstønadEps"),
                                statensLånekasse = intOrNull("statensLånekasse"),
                                statensLånekasseEps = intOrNull("statensLånekasseEps"),
                                supplerendeStønad = intOrNull("supplerendeStønad"),
                                supplerendeStønadEps = intOrNull("supplerendeStønadEps"),
                                sykepenger = intOrNull("sykepenger"),
                                sykepengerEps = intOrNull("sykepengerEps"),
                                tiltakspenger = intOrNull("tiltakspenger"),
                                tiltakspengerEps = intOrNull("tiltakspengerEpsEps"),
                                ventestønad = intOrNull("ventestønad"),
                                ventestønadEps = intOrNull("ventestønadEps"),
                                uføretrygd = intOrNull("uføretrygd"),
                                uføretrygdEps = intOrNull("uføretrygdEps"),
                                forventetInntekt = intOrNull("forventetInntekt"),
                                forventetInntektEps = intOrNull("forventetInntektEps"),
                                avkortingUtenlandsopphold = intOrNull("avkortingUtenlandsopphold"),
                                avkortingUtenlandsoppholdEps = intOrNull("avkortingUtenlandsoppholdEpsEps"),
                                underMinstenivå = intOrNull("underMinstenivå"),
                                underMinstenivåEps = intOrNull("underMinstenivåEpsEps"),
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

    private fun mapMånedstatistikk(måned: YearMonth, nyligste: StønadstatistikkDto): StønadstatistikkMåned {
        val månedsbeløp = nyligste.månedsbeløp.single {
            it.måned == måned.toString()
        }

        return StønadstatistikkMåned(
            id = UUID.randomUUID(),
            måned = måned,
            funksjonellTid = nyligste.funksjonellTid,
            tekniskTid = nyligste.tekniskTid,
            sakId = nyligste.sakId,
            stonadstype = nyligste.stonadstype,
            personnummer = nyligste.personnummer,
            personNummerEps = nyligste.personNummerEktefelle,
            vedtaksdato = nyligste.vedtaksdato,
            vedtakstype = nyligste.vedtakstype,
            vedtaksresultat = nyligste.vedtaksresultat,
            vedtakFraOgMed = nyligste.gjeldendeStonadVirkningstidspunkt,
            vedtakTilOgMed = nyligste.gjeldendeStonadStopptidspunkt,
            behandlendeEnhetKode = nyligste.behandlendeEnhetKode,
            opphorsgrunn = nyligste.opphorsgrunn,
            opphorsdato = nyligste.opphorsdato,
            harUtenlandsOpphold = nyligste.harUtenlandsOpphold,
            harFamiliegjenforening = nyligste.harFamiliegjenforening,
            flyktningsstatus = nyligste.flyktningsstatus,
            stonadsklassifisering = månedsbeløp.stonadsklassifisering,
            sats = månedsbeløp.sats,
            utbetales = månedsbeløp.utbetales,
            fradragSum = månedsbeløp.fradragSum,
            uføregrad = månedsbeløp.uføregrad,

            årsakStans = null, // TODO

            alderspensjon = fradragOmFinnes(
                Fradragstype.Kategori.Alderspensjon,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            alderspensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.Alderspensjon,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            arbeidsavklaringspenger = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsavklaringspenger,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            arbeidsavklaringspengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsavklaringspenger,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            arbeidsinntekt = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsinntekt,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            arbeidsinntektEps = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsinntekt,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            omstillingsstønad = fradragOmFinnes(
                Fradragstype.Kategori.Omstillingsstønad,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            omstillingsstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Omstillingsstønad,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            avtalefestetPensjon = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjon,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            avtalefestetPensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjon,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            avtalefestetPensjonPrivat = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjonPrivat,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            avtalefestetPensjonPrivatEps = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjonPrivat,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            bidragEtterEkteskapsloven = fradragOmFinnes(
                Fradragstype.Kategori.BidragEtterEkteskapsloven,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            bidragEtterEkteskapslovenEps = fradragOmFinnes(
                Fradragstype.Kategori.BidragEtterEkteskapsloven,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            dagpenger = fradragOmFinnes(Fradragstype.Kategori.Dagpenger, FradragTilhører.BRUKER, månedsbeløp.fradrag),
            dagpengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Dagpenger,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            fosterhjemsgodtgjørelse = fradragOmFinnes(
                Fradragstype.Kategori.Fosterhjemsgodtgjørelse,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            fosterhjemsgodtgjørelseEps = fradragOmFinnes(
                Fradragstype.Kategori.Fosterhjemsgodtgjørelse,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            gjenlevendepensjon = fradragOmFinnes(
                Fradragstype.Kategori.Gjenlevendepensjon,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            gjenlevendepensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.Gjenlevendepensjon,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            introduksjonsstønad = fradragOmFinnes(
                Fradragstype.Kategori.Introduksjonsstønad,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            introduksjonsstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Introduksjonsstønad,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            kapitalinntekt = fradragOmFinnes(
                Fradragstype.Kategori.Kapitalinntekt,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            kapitalinntektEps = fradragOmFinnes(
                Fradragstype.Kategori.Kapitalinntekt,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            kontantstøtte = fradragOmFinnes(
                Fradragstype.Kategori.Kontantstøtte,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            kontantstøtteEps = fradragOmFinnes(
                Fradragstype.Kategori.Kontantstøtte,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            kvalifiseringsstønad = fradragOmFinnes(
                Fradragstype.Kategori.Kvalifiseringsstønad,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            kvalifiseringsstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Kvalifiseringsstønad,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            navYtelserTilLivsopphold = fradragOmFinnes(
                Fradragstype.Kategori.NAVytelserTilLivsopphold,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            navYtelserTilLivsoppholdEps = fradragOmFinnes(
                Fradragstype.Kategori.NAVytelserTilLivsopphold,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            offentligPensjon = fradragOmFinnes(
                Fradragstype.Kategori.OffentligPensjon,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            offentligPensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.OffentligPensjon,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            privatPensjon = fradragOmFinnes(
                Fradragstype.Kategori.PrivatPensjon,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            privatPensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.PrivatPensjon,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            sosialstønad = fradragOmFinnes(
                Fradragstype.Kategori.Sosialstønad,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            sosialstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Sosialstønad,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            statensLånekasse = fradragOmFinnes(
                Fradragstype.Kategori.StatensLånekasse,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            statensLånekasseEps = fradragOmFinnes(
                Fradragstype.Kategori.StatensLånekasse,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            supplerendeStønad = fradragOmFinnes(
                Fradragstype.Kategori.SupplerendeStønad,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            supplerendeStønadEps = fradragOmFinnes(
                Fradragstype.Kategori.SupplerendeStønad,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            sykepenger = fradragOmFinnes(
                Fradragstype.Kategori.Sykepenger,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            sykepengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Sykepenger,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            tiltakspenger = fradragOmFinnes(
                Fradragstype.Kategori.Tiltakspenger,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            tiltakspengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Tiltakspenger,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            ventestønad = fradragOmFinnes(
                Fradragstype.Kategori.Ventestønad,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            ventestønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Ventestønad,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            uføretrygd = fradragOmFinnes(
                Fradragstype.Kategori.Uføretrygd,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            uføretrygdEps = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsinntekt,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            forventetInntekt = fradragOmFinnes(
                Fradragstype.Kategori.ForventetInntekt,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            forventetInntektEps = fradragOmFinnes(
                Fradragstype.Kategori.ForventetInntekt,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            avkortingUtenlandsopphold = fradragOmFinnes(
                Fradragstype.Kategori.AvkortingUtenlandsopphold,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            avkortingUtenlandsoppholdEps = fradragOmFinnes(
                Fradragstype.Kategori.AvkortingUtenlandsopphold,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            underMinstenivå = fradragOmFinnes(
                Fradragstype.Kategori.UnderMinstenivå,
                FradragTilhører.BRUKER,
                månedsbeløp.fradrag,
            ),
            underMinstenivåEps = fradragOmFinnes(
                Fradragstype.Kategori.UnderMinstenivå,
                FradragTilhører.EPS,
                månedsbeløp.fradrag,
            ),

            annet = fradragOmFinnes(Fradragstype.Kategori.Annet, FradragTilhører.BRUKER, månedsbeløp.fradrag),
            annetEps = fradragOmFinnes(Fradragstype.Kategori.Annet, FradragTilhører.EPS, månedsbeløp.fradrag),
        )
    }

    private fun fradragOmFinnes(
        fradragstype: Fradragstype.Kategori,
        tilhører: FradragTilhører,
        fradrag: List<Fradrag>,
    ): Int? {
        return fradrag.singleOrNull {
            it.fradragstype == fradragstype.name && it.tilhører == tilhører.name
        }?.beløp?.toInt()
    }
}
