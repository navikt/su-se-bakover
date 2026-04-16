import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import kotlin.reflect.KClass

fun interface LeggTilVedtaksbrevvalg {
    fun leggTilBrevvalg(brevvalgSøknadsbehandling: BrevvalgSøknadsbehandling.Valgt): Søknadsbehandling
}

sealed interface KunneIkkeLeggeTilVedtaksbrevvalgSøknad {
    data class UgyldigTilstand(
        val tilstand: KClass<out Søknadsbehandling>,
    ) : KunneIkkeLeggeTilVedtaksbrevvalgSøknad
}
