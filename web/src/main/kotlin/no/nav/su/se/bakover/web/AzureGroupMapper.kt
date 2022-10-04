package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Brukerrolle

class AzureGroupMapper(private val azureGroups: ApplicationConfig.AzureConfig.AzureGroups) {
    fun fromAzureGroup(group: String): Brukerrolle? {
        return when (group) {
            azureGroups.attestant -> Brukerrolle.Attestant
            azureGroups.saksbehandler -> Brukerrolle.Saksbehandler
            azureGroups.veileder -> Brukerrolle.Veileder
            azureGroups.drift -> Brukerrolle.Drift
            else -> null
        }
    }
}
