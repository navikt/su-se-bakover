package no.nav.su.se.bakover.common.audit.infrastructure

import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.audit.application.AuditLogger
import no.nav.su.se.bakover.common.auditLogg
import java.lang.String.join

enum class CefFieldName(val kode: String) {
    /**
     * Tidspunkt for når hendelsen skjedde.
     */
    EVENT_TIME("end"),

    /**
     * Brukeren som startet hendelsen (saksbehandler/veileder/...).
     */
    USER_ID("suid"),

    /**
     * Bruker (søker/part/...) som har personopplysninger som blir berørt.
     */
    BERORT_BRUKER_ID("duid"),

    /**
     * Reservert til bruk for "Behandling". Det er godkjent med både behandlingsUuid
     * og behandlingsId, men førstnevnte er foretrukket. Denne skal unikt identifisere
     * behandlingen.
     */
    BEHANDLING_VERDI("flexString2"),

    /**
     * Reservert til bruk for "behandlingId".
     */
    BEHANDLING_LABEL("flexString2Label"),

    /**
     * Call-id, prosess-id
     */
    CALL_ID("sproc"),

    /**
     * Om handlingen blir tillatt eller ikke (permit/deny)
     */
    DECISION_VERDI("flexString1"),
    DECISION_LABEL("flexString1Label"),
    ;
}

data class CefField(val cefFieldName: CefFieldName, val value: String)

/**
 * Logger til auditlogg på formatet
 *
 * CEF:0|su-se-bakover|auditLog|1.0|audit:access|su-se-bakover audit log|INFO|
 * end=1618308696856 suid=X123456 duid=01010199999
 * flexString1Label=Decision flexString1=Permit
 * flexString2Label=behandlingId flexString2=2dc4c100-395a-4e25-b1e9-6ea52f49b9e1
 * sproc=40e4608e-7157-415d-86c2-697f4c3c7358
 */
object CefAuditLogger : AuditLogger {
    private const val applicationName = "su-se-bakover"

    override fun log(logEvent: AuditLogEvent) {
        auditLogg.info(compileLogMessage(logEvent))
    }

    private fun compileLogMessage(logEvent: AuditLogEvent): String {
        // Field descriptions from CEF documentation (#tech-logg_analyse_og_datainnsikt):
        /*
        Set to: 0 (zero)
         */
        val version = "CEF:0"
        /*
        Arena, Bisys etc
         */
        val deviceVendor = applicationName
        /*
        The name of the log that originated the event. Auditlog, leselogg, ABAC-Audit, Sporingslogg
         */
        val deviceProduct = "auditLog"
        /*
        The version of the logformat. 1.0
         */
        val deviceVersion = "1.0"
        /*
        The text representing the type of the event. For example audit:access, audit:edit
         */
        val deviceEventClassId = logEvent.action.value
        /*
        The description of the event. For example 'ABAC sporingslogg' or 'Database query'
         */
        val name = "$applicationName audit log"
        /*
        The severity of the event (INFO or WARN)
         */
        val severity = "INFO"

        val extensions = join(" ", getExtensions(logEvent).map { "${it.cefFieldName.kode}=${it.value}" })

        return join(
            "|",
            listOf(
                version,
                deviceVendor,
                deviceProduct,
                deviceVersion,
                deviceEventClassId,
                name,
                severity,
                extensions,
            ),
        )
    }

    private fun getExtensions(logEvent: AuditLogEvent): List<CefField> =
        listOfNotNull(
            CefField(CefFieldName.EVENT_TIME, System.currentTimeMillis().toString()),
            CefField(CefFieldName.USER_ID, logEvent.navIdent),
            CefField(CefFieldName.BERORT_BRUKER_ID, logEvent.berørtBrukerId.toString()),
            CefField(CefFieldName.DECISION_LABEL, "Decision"),
            CefField(CefFieldName.DECISION_VERDI, "Permit"),
            CefField(CefFieldName.CALL_ID, logEvent.callId.toString()),
        ).plus(
            logEvent.behandlingId?.let {
                listOf(
                    CefField(CefFieldName.BEHANDLING_LABEL, "behandlingId"),
                    CefField(CefFieldName.BEHANDLING_VERDI, it.toString()),
                )
            }.orEmpty(),
        )
}
