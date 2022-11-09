package no.nav.su.se.bakover.domain.dokument

sealed class KunneIkkeLageDokument {
    override fun toString() = this::class.simpleName!!

    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageDokument()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageDokument()
    object KunneIkkeHentePerson : KunneIkkeLageDokument()
    object KunneIkkeGenererePDF : KunneIkkeLageDokument()
    object DetSkalIkkeSendesBrev : KunneIkkeLageDokument()
}
