package no.nav.su.se.bakover.common.toggle.infrastructure

import io.getunleash.Unleash
import no.nav.su.se.bakover.common.toggle.domain.ToggleClient

class UnleashToggleClient(
    private val unleash: Unleash,
) : ToggleClient {
    override fun isEnabled(toggleName: String): Boolean {
        return unleash.isEnabled(toggleName)
    }
}
