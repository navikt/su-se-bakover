import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import kotlin.reflect.KClass

fun interface LeggTilVedtaksbrevvalgSøknadsbehandling {
    fun leggTilBrevvalg(brevvalgSøknadsbehandling: BrevvalgBehandling.Valgt): Søknadsbehandling
}

sealed interface KunneIkkeLeggeTilVedtaksbrevvalgSøknad {
    data class UgyldigTilstand(
        val tilstand: KClass<out Søknadsbehandling>,
    ) : KunneIkkeLeggeTilVedtaksbrevvalgSøknad
}
