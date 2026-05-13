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
                BrevvalgBehandling.Valgt.SendBrev(
                    bestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                    begrunnelse = null,
                )
            }

            Valg.IKKE_SEND -> {
                BrevvalgBehandling.Valgt.IkkeSendBrev(
                    bestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                    begrunnelse = null,
                )
            }
        }
    }
}
