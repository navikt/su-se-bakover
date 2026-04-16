import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId

data class LeggTilBrevvalgRequestSøknad(
    val søknadsbehandlingId: SøknadsbehandlingId,
    val valg: Valg,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    enum class Valg {
        SEND,
        IKKE_SEND,
    }

    fun toDomain(): BrevvalgSøknadsbehandling.Valgt {
        return when (valg) {
            Valg.SEND -> {
                BrevvalgSøknadsbehandling.Valgt.SendBrev(
                    bestemtAv = BrevvalgSøknadsbehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }

            Valg.IKKE_SEND -> {
                BrevvalgSøknadsbehandling.Valgt.IkkeSendBrev(
                    bestemtAv = BrevvalgSøknadsbehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }
        }
    }
}
