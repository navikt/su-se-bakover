package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed interface KunneIkkeHenteFritekstTilBrev {
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeHenteFritekstTilBrev
}
