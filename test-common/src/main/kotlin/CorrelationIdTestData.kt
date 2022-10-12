package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.CorrelationId
import java.util.UUID

fun correlationId() = CorrelationId(UUID.randomUUID().toString())
