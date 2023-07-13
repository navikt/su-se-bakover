package no.nav.su.se.bakover.domain.dokument

sealed class KunneIkkeLageDokument {
    override fun toString() = this::class.simpleName!!

    data object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageDokument()
    data object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageDokument()
    data object KunneIkkeHentePerson : KunneIkkeLageDokument()
    data object KunneIkkeGenererePDF : KunneIkkeLageDokument()
    data object DetSkalIkkeSendesBrev : KunneIkkeLageDokument()
}
