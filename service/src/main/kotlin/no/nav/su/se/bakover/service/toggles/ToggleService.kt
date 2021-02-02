package no.nav.su.se.bakover.service.toggles

interface ToggleService {
    fun isEnabled(toggleName: String): Boolean
}
