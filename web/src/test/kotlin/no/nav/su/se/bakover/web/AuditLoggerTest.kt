package no.nav.su.se.bakover.web

import org.junit.jupiter.api.Test
import java.util.UUID

internal class AuditLoggerTest {

    @Test
    fun `output til auditlog ser ut som den skal`() {
        AuditLogger.log(
            AuditLogEvent(
                "X123456",
                FnrGenerator.random(),
                AuditLogEvent.Action.ACCESS,
                UUID.randomUUID(),
                UUID.randomUUID().toString()
            )
        )
    }
}
