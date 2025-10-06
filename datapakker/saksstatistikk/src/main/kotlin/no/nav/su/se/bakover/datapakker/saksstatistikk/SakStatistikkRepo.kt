package no.nav.su.se.bakover.datapakker.saksstatistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

object SakStatistikkRepo {
    fun hentData(dataSource: DataSource): List<SakStatistikk> {
        return emptyList()
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SakStatistikk(
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
