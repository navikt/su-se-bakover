package no.nav.su.se.bakover.datapakker.saksstatistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.use

object SakStatistikkRepo {

    fun hentData(dataSource: DataSource, dag: LocalDate): List<SakStatistikk> =
        hentData(dataSource, dag, dag.plusDays(1))

    fun hentData(dataSource: DataSource, fom: LocalDate, tom: LocalDate): List<SakStatistikk> {
        return dataSource.connection.use {
            val session = sessionOf(dataSource)
            """
                SELECT * FROM sak_statistikk
                WHERE hendelse_tid > :fom and hendelse_tid < :tom 
            """.trimIndent().hentListe(
                params = mapOf(
                    "fom" to fom,
                    "tom" to tom,
                ),
                session = session,
            ) { row ->
                SakStatistikk(
                    id = row.uuid("id"),
                    hendelseTid = row.string("hendelse_tid"),
                    tekniskTid = row.string("teknisk_tid"),
                    sakId = row.uuid("sak_id"),
                    saksnummer = row.long("saksnummer"),
                    behandlingId = row.uuid("behandling_id"),
                    relatertBehandlingId = row.uuidOrNull("relatert_behandling_id"),
                    aktorId = row.string("aktorid"),
                    sakYtelse = row.string("sak_ytelse"),
                    sakUtland = row.string("sak_utland"),
                    behandlingType = row.string("behandling_type"),
                    behandlingMetode = row.string("behandling_metode"),
                    mottattTid = row.string("mottatt_tid"),
                    registrertTid = row.string("registrert_tid"),
                    ferdigbehandletTid = row.stringOrNull("ferdigbehandlet_tid"),
                    utbetaltTid = row.localDateOrNull("utbetalt_tid"),
                    behandlingStatus = row.string("behandling_status"),
                    behandlingResultat = row.stringOrNull("behandling_resultat"),
                    resultatBegrunnelse = row.stringOrNull("behandling_begrunnelse"),
                    behandlingAarsak = row.stringOrNull("behandling_aarsak"),
                    opprettetAv = row.string("opprettet_av"),
                    saksbehandler = row.stringOrNull("saksbehandler"),
                    ansvarligBeslutter = row.stringOrNull("ansvarlig_beslutter"),
                    ansvarligEnhet = row.string("ansvarlig_enhet"),
                    vedtaksløsningNavn = row.string("vedtakslosning_navn"),
                    funksjonellPeriodeFom = row.localDateOrNull("funksjonell_periode_fom"),
                    funksjonellPeriodeTom = row.localDateOrNull("funksjonell_periode_tom"),
                    tilbakekrevBeløp = row.longOrNull("tilbakekrev_beloep"),
                )
            }
        }
    }

    private fun <T> String.hentListe(
        params: Map<String, Any> = emptyMap(),
        session: Session,
        rowMapping: (Row) -> T,
    ): List<T> {
        return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SakStatistikk(
    val id: UUID,
    val hendelseTid: String,
    val tekniskTid: String,
    val sakId: UUID,
    @param:JsonSerialize(using = ToStringSerializer::class)
    val saksnummer: Long,
    val behandlingId: UUID,
    val relatertBehandlingId: UUID? = null,
    val aktorId: String,

    val sakYtelse: String,
    val sakUtland: String = "NASJONAL",
    val behandlingType: String,

    val behandlingMetode: String,

    val mottattTid: String,
    val registrertTid: String,
    val ferdigbehandletTid: String? = null,
    val utbetaltTid: LocalDate? = null,

    val behandlingStatus: String,
    val behandlingResultat: String? = null,
    val resultatBegrunnelse: String? = null,
    val behandlingAarsak: String? = null,

    val opprettetAv: String?,
    val saksbehandler: String? = null,
    // Attestant
    val ansvarligBeslutter: String? = null,
    val ansvarligEnhet: String = "4815",

    val vedtaksløsningNavn: String = "SU-App",

    // Tilbakekreving
    val funksjonellPeriodeFom: LocalDate? = null,
    val funksjonellPeriodeTom: LocalDate? = null,
    val tilbakekrevBeløp: Long? = null,
)

fun List<SakStatistikk>.toCsv(): String = buildString {
    for (sakStatistikk in this@toCsv) {
        appendLine(
            listOf(
                sakStatistikk.hendelseTid,
                sakStatistikk.tekniskTid,
                sakStatistikk.sakId.toString(),
                sakStatistikk.saksnummer.toString(),
                sakStatistikk.behandlingId.toString(),
                sakStatistikk.relatertBehandlingId.toString(),
                sakStatistikk.aktorId,
                sakStatistikk.sakYtelse,
                sakStatistikk.sakUtland,
                sakStatistikk.behandlingType,
                sakStatistikk.behandlingMetode,
                sakStatistikk.mottattTid,
                sakStatistikk.registrertTid,
                sakStatistikk.ferdigbehandletTid.orEmpty(),
                sakStatistikk.utbetaltTid?.toString().orEmpty(),
                sakStatistikk.behandlingStatus,
                sakStatistikk.behandlingResultat.orEmpty(),
                sakStatistikk.resultatBegrunnelse.orEmpty(),
                sakStatistikk.behandlingAarsak.orEmpty(),
                sakStatistikk.opprettetAv.orEmpty(),
                sakStatistikk.saksbehandler.orEmpty(),
                sakStatistikk.ansvarligBeslutter.orEmpty(),
                sakStatistikk.ansvarligEnhet,
                sakStatistikk.vedtaksløsningNavn,
                sakStatistikk.funksjonellPeriodeFom?.toString().orEmpty(),
                sakStatistikk.funksjonellPeriodeTom?.toString().orEmpty(),
                sakStatistikk.tilbakekrevBeløp?.toString().orEmpty(),
            ),
        )
    }
}
