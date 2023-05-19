package no.nav.su.se.bakover.common.infrastructure.brukerrolle

import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle

class AzureGroupMapper(private val azureGroups: AzureGroups) {
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
