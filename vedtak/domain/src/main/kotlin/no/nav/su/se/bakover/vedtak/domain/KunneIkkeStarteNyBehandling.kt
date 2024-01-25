package no.nav.su.se.bakover.vedtak.domain

sealed interface KunneIkkeStarteNyBehandling {
    data object FantIkkeVedtak : KunneIkkeStarteNyBehandling
    data object FantIkkeSak : KunneIkkeStarteNyBehandling
    data object VedtakKanIkkeStarteEnNyBehandling : KunneIkkeStarteNyBehandling
}
