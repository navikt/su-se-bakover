package no.nav.su.se.bakover.test.persistence

import io.kotest.assertions.fail
import no.nav.su.se.bakover.common.persistence.SessionCounter

val sessionCounterStub: SessionCounter = SessionCounter {
    fail("Database sessions were over the threshold while running test.")
}
