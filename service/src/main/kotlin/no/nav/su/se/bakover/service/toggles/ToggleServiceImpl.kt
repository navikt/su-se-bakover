package no.nav.su.se.bakover.service.toggles

import no.finn.unleash.Unleash

class ToggleServiceImpl(
    private val unleash: Unleash,
) : ToggleService {
    override fun isEnabled(toggleName: String): Boolean {
        return unleash.isEnabled(toggleName)
    }
}
