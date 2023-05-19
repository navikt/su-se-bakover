package no.nav.su.se.bakover.common.featuretoggle

interface ToggleClient {
    fun isEnabled(toggleName: String): Boolean
}
