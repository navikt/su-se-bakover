package no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import kotlin.reflect.KClass

sealed interface KunneIkkeGenerereBrevutkastForSøknadsbehandling {

    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
    ) : KunneIkkeGenerereBrevutkastForSøknadsbehandling {
        val til: KClass<out PdfA> = PdfA::class
    }

    data class UnderliggendeFeil(
        val underliggende: KunneIkkeLageDokument,
    ) : KunneIkkeGenerereBrevutkastForSøknadsbehandling
}
