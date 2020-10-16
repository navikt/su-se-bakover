package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Config

enum class Brukerrolle(val type: String) {
    Attestant("ATTESTANT"),
    Saksbehandler("SAKSBEHANDLER"),
    Veileder("VEILEDER");

    companion object {
        fun fromAzureGroup(group: String) =
            when (group) {
                Config.azureGroupAttestant -> Attestant
                Config.azureGroupSaksbehandler -> Saksbehandler
                Config.azureGroupVeileder -> Veileder
                else -> null
            }

        fun toAzureGroup(rolle: Brukerrolle) =
            when (rolle) {
                Attestant -> Config.azureGroupAttestant
                Saksbehandler -> Config.azureGroupSaksbehandler
                Veileder -> Config.azureGroupVeileder
            }
    }
}
