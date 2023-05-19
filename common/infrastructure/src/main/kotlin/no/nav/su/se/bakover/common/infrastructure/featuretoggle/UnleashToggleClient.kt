package no.nav.su.se.bakover.common.infrastructure.featuretoggle

import io.getunleash.Unleash
import no.nav.su.se.bakover.common.featuretoggle.ToggleClient

class UnleashToggleClient(
    private val unleash: Unleash,
) : ToggleClient {
    override fun isEnabled(toggleName: String): Boolean {
        return unleash.isEnabled(toggleName)
    }
}
