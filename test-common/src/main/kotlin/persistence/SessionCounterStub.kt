package no.nav.su.se.bakover.test.persistence

import io.kotest.assertions.fail
import no.nav.su.se.bakover.common.infrastructure.persistence.SessionValidator

val sessionValidatorStub: SessionValidator = SessionValidator {
    fail("Database sessions were over the threshold while running test.")
}
