package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed interface KunneIkkeAvslutteKlage {
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeAvslutteKlage {
        val til: KClass<AvsluttetKlage> = AvsluttetKlage::class
    }

    data object FantIkkeKlage : KunneIkkeAvslutteKlage
}
