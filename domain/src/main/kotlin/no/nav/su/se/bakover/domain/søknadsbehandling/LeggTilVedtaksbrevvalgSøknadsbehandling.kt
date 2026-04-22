import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.BrevvalgSøknadsbehandling
import kotlin.reflect.KClass

fun interface LeggTilVedtaksbrevvalgSøknadsbehandling {
    fun leggTilBrevvalg(brevvalgSøknadsbehandling: BrevvalgSøknadsbehandling.Valgt): Søknadsbehandling
}

sealed interface KunneIkkeLeggeTilVedtaksbrevvalgSøknad {
    data class UgyldigTilstand(
        val tilstand: KClass<out Søknadsbehandling>,
    ) : KunneIkkeLeggeTilVedtaksbrevvalgSøknad
}
