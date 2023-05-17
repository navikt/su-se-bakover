package no.nav.su.se.bakover.common.audit

interface AuditLogger {
    fun log(logEvent: AuditLogEvent)
}
