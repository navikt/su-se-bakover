package no.nav.su.se.bakover.domain.dokument

sealed class KunneIkkeLageDokument {
    data object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageDokument()
    data object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageDokument()
    data object KunneIkkeHentePerson : KunneIkkeLageDokument()
    data object KunneIkkeGenererePDF : KunneIkkeLageDokument()
    data object DetSkalIkkeSendesBrev : KunneIkkeLageDokument()
}
