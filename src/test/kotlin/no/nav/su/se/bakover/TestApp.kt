package no.nav.su.se.bakover

import io.ktor.application.Application
import io.mockk.every
import io.mockk.mockk
import no.nav.su.se.bakover.person.SuPersonClient

fun Application.testApp(environment: Environment = configureDefaultEnv(), suPersonClient: SuPersonClient = mockk()) {
    return susebakover(environment, suPersonClient)
}

fun configureDefaultEnv(): Environment {
    val environment = mockk<Environment>()
    every { environment.allowCorsOrigin } returns "host.server.no"
    return environment
}