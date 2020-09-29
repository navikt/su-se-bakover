package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Config

enum class Brukerrolle(val type: String) {
    Attestant("ATTESTANT"),
    Saksbehandler("SAKSBEHANDLER"),
    Veileder("VEILEDER");

    companion object {
        fun fromAzureGroup(group: String) =
            when (group) {
                Config.azureGroupAttestant -> Brukerrolle.Attestant
                Config.azureGroupSaksbehandler -> Brukerrolle.Saksbehandler
                Config.azureGroupVeileder -> Brukerrolle.Veileder
                else -> null
            }
    }
}
