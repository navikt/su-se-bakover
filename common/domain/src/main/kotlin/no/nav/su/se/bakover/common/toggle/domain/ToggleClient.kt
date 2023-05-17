package no.nav.su.se.bakover.common.toggle.domain

interface ToggleClient {
    fun isEnabled(toggleName: String): Boolean
}
