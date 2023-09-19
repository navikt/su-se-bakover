package no.nav.su.se.bakover.domain.brev.jsonRequest

import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent

sealed interface FeilVedHentingAvInformasjon {
    data class KunneIkkeHentePerson(
        val underliggende: person.domain.KunneIkkeHentePerson,
    ) : FeilVedHentingAvInformasjon

    data class KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
        val underliggende: KunneIkkeHenteNavnForNavIdent,
    ) : FeilVedHentingAvInformasjon
}
