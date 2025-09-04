package no.nav.su.se.bakover.datapakker.stoenadstatistikk

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource
import kotlin.use

private fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): List<T> {
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
}

fun hentData(dataSource: DataSource, måned: YearMonth): List<StønadstatistikkMånedDto> {
    return dataSource.connection.use {
        val session = sessionOf(dataSource)
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
                    StønadstatistikkMånedDto(
                        id = id,
                        måned = måned,
                        funksjonellTid = string("funksjonell_tid"),
                        tekniskTid = string("teknisk_tid"),
                        sakId = UUID.fromString(string("sak_id")),
                        stonadstype = string("stonadstype"),
                        vedtaksdato = localDate("vedtaksdato"),
                        personnummer = string("personnummer"),
                        personNummerEps = stringOrNull("personnummer_eps"),
                        vedtakFraOgMed = localDate("vedtak_fra_og_med"),
                        vedtakTilOgMed = localDate("vedtak_til_og_med"),
                        vedtakstype = string("vedtakstype"),
                        vedtaksresultat = string("vedtaksresultat"),
                        opphorsgrunn = stringOrNull("opphorsgrunn"),
                        opphorsdato = localDateOrNull("opphorsdato"),
                        behandlendeEnhetKode = string("behandlende_enhet_kode"),
                        harUtenlandsOpphold = stringOrNull("har_utenlandsopphold"),
                        harFamiliegjenforening = stringOrNull("har_familiegjenforening"),
                        flyktningsstatus = stringOrNull("flyktningsstatus"),
                        månedsbeløp = hentMånedsbeløp(session, id).singleOrNull(),
                    )
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
            val stonadsklassifisering = row.string("stonadsklassifisering")
            val sats = row.long("sats")
            val utbetales = row.long("utbetales")
            val fradragSum = row.long("fradrag_sum")
            val uføregrad = row.intOrNull("uforegrad")

            Månedsbeløp(
                manedsbelopId = manedsbelopId.toString(),
                måned = maaned,
                stonadsklassifisering = stonadsklassifisering,
                sats = sats,
                utbetales = utbetales,
                fradragSum = fradragSum,
                uføregrad = uføregrad,
                fradrag = hentInntekter(session, manedsbelopId),
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
