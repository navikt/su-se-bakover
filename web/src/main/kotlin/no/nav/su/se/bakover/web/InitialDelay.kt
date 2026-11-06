package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import java.time.Duration

/**
 * Muliggjør forsinkelse av jobber/tasks.
 * Not thread safe.
 */
class InitialDelay(
    private val runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
    initialDelayLocal: Duration = Duration.ofSeconds(5),
    initialDelayNais: Duration = Duration.ofMinutes(5),
    private val nextDelayLocal: Duration = Duration.ofSeconds(5),
    private val nextDelayNais: Duration = Duration.ofSeconds(30),
) {
    private var initialDelay: Duration =
        if (runtimeEnvironment.erNais()) {
            initialDelayNais
        } else {
            initialDelayLocal
        }

    fun next(): Duration {
        return initialDelay.also {
            initialDelay = if (runtimeEnvironment.erNais()) {
                initialDelay.plus(nextDelayNais)
            } else {
                initialDelay.plus(nextDelayLocal)
            }
        }
    }
}
