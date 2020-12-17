package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.Brukerrolle

class AzureGroupMapper(private val azureGroups: Config.AzureConfig.AzureGroups) {
    fun fromAzureGroup(group: String): Brukerrolle? {
        return when (group) {
            azureGroups.attestant -> Brukerrolle.Attestant
            azureGroups.saksbehandler -> Brukerrolle.Saksbehandler
            azureGroups.veileder -> Brukerrolle.Veileder
            else -> null
        }
    }
}
