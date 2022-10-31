package no.nav.su.se.bakover.common.audit.application

interface AuditLogger {
    fun log(logEvent: AuditLogEvent)
}
