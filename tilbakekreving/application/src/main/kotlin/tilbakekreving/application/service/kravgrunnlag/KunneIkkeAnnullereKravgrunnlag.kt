package tilbakekreving.application.service.kravgrunnlag

import tilbakekreving.domain.vedtak.KunneIkkeAnnullerePåbegynteVedtak
import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeAnnullereKravgrunnlag {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeAnnullereKravgrunnlag
    data object InnsendtHendelseIdErIkkeDenSistePåSaken : KunneIkkeAnnullereKravgrunnlag
    data object SakenHarIkkeKravgrunnlagSomKanAnnulleres : KunneIkkeAnnullereKravgrunnlag
    data object FantIkkeKravgrunnlag : KunneIkkeAnnullereKravgrunnlag
    data object BehandlingenErIFeilTilstandForÅAnnullere : KunneIkkeAnnullereKravgrunnlag
    data class FeilMotTilbakekrevingskomponenten(val underliggende: KunneIkkeAnnullerePåbegynteVedtak) : KunneIkkeAnnullereKravgrunnlag
}
