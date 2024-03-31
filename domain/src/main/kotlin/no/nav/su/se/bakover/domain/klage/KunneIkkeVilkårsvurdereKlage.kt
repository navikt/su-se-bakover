package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed interface KunneIkkeVilkårsvurdereKlage {
    data object FantIkkeKlage : KunneIkkeVilkårsvurdereKlage
    data object FantIkkeVedtak : KunneIkkeVilkårsvurdereKlage
    data object KanIkkeAvviseEnKlageSomHarVærtOversendt : KunneIkkeVilkårsvurdereKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeVilkårsvurdereKlage {
        val til = VilkårsvurdertKlage::class
    }

    data object VedtakSkalIkkeSendeBrev : KunneIkkeVilkårsvurdereKlage
}
