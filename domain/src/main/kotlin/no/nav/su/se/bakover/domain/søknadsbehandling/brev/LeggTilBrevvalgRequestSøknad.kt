import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgBehandling
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

    fun toDomain(): BrevvalgBehandling.Valgt {
        return when (valg) {
            Valg.SEND -> {
                BrevvalgBehandling.Valgt.SendBrev.opprett(
                    bestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }

            Valg.IKKE_SEND -> {
                BrevvalgBehandling.Valgt.IkkeSendBrev.opprett(
                    bestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }
        }
    }
}
