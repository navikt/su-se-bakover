package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.common.CallContext

internal fun setCallContextForTests() {
    CallContext(
        CallContext.SecurityContext("token"),
        CallContext.MdcContext(mapOf("X-Correlation-ID" to "correlationId"))
    )
}
