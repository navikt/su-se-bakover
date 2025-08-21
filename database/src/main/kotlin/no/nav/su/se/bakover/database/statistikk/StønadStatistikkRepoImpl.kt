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
import statistikk.domain.StønadstatistikkDto.Inntekt
import statistikk.domain.StønadstatistikkDto.Månedsbeløp
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
                    id, har_utenlandsopphold, har_familiegjenforening, aar_maaned, personnummer,
                    personnummer_ektefelle, funksjonell_tid, teknisk_tid, stonadstype, sak_id, vedtaksdato,
                    vedtakstype, vedtaksresultat, behandlende_enhet_kode, ytelse_virkningstidspunkt,
                    gjeldende_stonad_virkningstidspunkt, gjeldende_stonad_stopptidspunkt,
                    gjeldende_stonad_utbetalingsstart, gjeldende_stonad_utbetalingsstopp, opphorsgrunn,
                    opphorsdato, flyktningsstatus, versjon
                    ) VALUES (
                        :id, :har_utenlandsopphold, :har_familiegjenforening, :aar_maaned, :personnummer,
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
                            "har_utenlandsopphold" to dto.harUtenlandsOpphold?.name,
                            "har_familiegjenforening" to dto.harFamiliegjenforening?.name,
                            "aar_maaned" to dto.statistikkAarMaaned.atDay(1),
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
                            "flyktningsstatus" to dto.flyktningsstatus,
                            "versjon" to dto.versjon,
                        ),
                        session = session,
                    )

                dto.månedsbeløp.forEach { mb ->
                    val manedsbelopId = UUID.randomUUID()
                    """
                    INSERT INTO manedsbelop (
                        id, stoenad_statistikk_id, maaned, stonadsklassifisering, bruttosats, nettosats, fradrag_sum
                    ) VALUES (
                        :id, :stoenad_statistikk_id, :maaned, :stonadsklassifisering, :bruttosats, :nettosats, :fradrag_sum
                    )
                    """.trimIndent()
                        .insert(
                            mapOf(
                                "id" to manedsbelopId,
                                "stoenad_statistikk_id" to stoenadStatistikkId,
                                "maaned" to mb.måned,
                                "stonadsklassifisering" to mb.stonadsklassifisering.name,
                                "bruttosats" to mb.bruttosats,
                                "nettosats" to mb.nettosats,
                                "fradrag_sum" to mb.fradragSum,
                            ),
                            session = session,
                        )
                    mb.inntekter.forEach { inntekt ->
                        val inntektId = UUID.randomUUID()
                        """
                        INSERT INTO inntekt (
                            id, manedsbelop_id, inntektstype, belop, tilhorer, er_utenlandsk
                        ) VALUES (
                            :id, :manedsbelop_id, :inntektstype, :belop, :tilhorer, :er_utenlandsk
                        )
                        """.trimIndent()
                            .insert(
                                mapOf(
                                    "id" to inntektId,
                                    "manedsbelop_id" to manedsbelopId,
                                    "inntektstype" to inntekt.inntektstype,
                                    "belop" to inntekt.beløp,
                                    "tilhorer" to inntekt.tilhører,
                                    "er_utenlandsk" to inntekt.erUtenlandsk,
                                ),
                                session = session,
                            )
                    }
                }
            }
        }
    }

    override fun hentHendelserForFnr(fnr: Fnr): List<StønadstatistikkDto> {
        return dbMetrics.timeQuery("hentHendelseStønadstatistikkDto") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    id, har_utenlandsopphold, har_familiegjenforening, aar_maaned, personnummer,
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
                        row.toStønadsstatistikk(session)
                    }
            }
        }
    }

    override fun hentStatistikkForMåned(måned: YearMonth): List<StønadstatistikkDto> {
        return dbMetrics.timeQuery("hentStatistikkForMåned") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    id, har_utenlandsopphold, har_familiegjenforening, aar_maaned, personnummer,
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
                        row.toStønadsstatistikk(session)
                    }.groupBy { it.sakId }.map {
                        val nyligste = it.value.maxBy { it.vedtaksdato }
                        nyligste
                    }
            }
        }
    }

    private fun Row.toStønadsstatistikk(session: Session): StønadstatistikkDto {
        val stoendStatistikkId = uuid("id")
        val harUtenlandsOpphold = stringOrNull("har_utenlandsopphold")?.let { JaNei.valueOf(it) }
        val harFamiliegjenforening = stringOrNull("har_familiegjenforening")?.let { JaNei.valueOf(it) }
        val statistikkAarMaaned = YearMonth.from(localDate("aar_maaned"))
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
        val flyktningsstatus = stringOrNull("flyktningsstatus")
        val versjon = stringOrNull("versjon")

        return StønadstatistikkDto(
            harUtenlandsOpphold = harUtenlandsOpphold,
            harFamiliegjenforening = harFamiliegjenforening,
            statistikkAarMaaned = statistikkAarMaaned,
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
            versjon = versjon,
            månedsbeløp = hentMånedsbeløp(session, stoendStatistikkId),
        )
    }

    private fun hentMånedsbeløp(session: Session, stoenadStatistikkId: UUID): List<Månedsbeløp> {
        return """
        SELECT id, maaned, stonadsklassifisering, bruttosats, nettosats, fradrag_sum
        FROM manedsbelop
        WHERE stoenad_statistikk_id = :stoenad_statistikk_id
        """.trimIndent()
            .hentListe(
                params = mapOf("stoenad_statistikk_id" to stoenadStatistikkId),
                session = session,
            ) { row ->
                val manedsbelopId = UUID.fromString(row.string("id"))
                val maaned = row.string("maaned")
                val stonadsklassifisering = StønadsklassifiseringDto.valueOf(row.string("stonadsklassifisering"))
                val bruttosats = row.long("bruttosats")
                val nettosats = row.long("nettosats")
                val fradragSum = row.long("fradrag_sum")

                Månedsbeløp(
                    måned = maaned,
                    stonadsklassifisering = stonadsklassifisering,
                    bruttosats = bruttosats,
                    nettosats = nettosats,
                    inntekter = hentInntekter(session, manedsbelopId),
                    fradragSum = fradragSum,
                )
            }
    }

    private fun hentInntekter(session: Session, manedsbelop_id: UUID): List<Inntekt> {
        return """
            SELECT inntektstype, belop, tilhorer, er_utenlandsk
            FROM inntekt
            WHERE manedsbelop_id = :manedsbelop_id
        """.trimIndent()
            .hentListe(
                params = mapOf("manedsbelop_id" to manedsbelop_id),
                session = session,
            ) { row ->
                Inntekt(
                    inntektstype = row.string("inntektstype"),
                    beløp = row.long("belop"),
                    tilhører = row.string("tilhorer"),
                    erUtenlandsk = row.boolean("er_utenlandsk"),
                )
            }
    }
}
