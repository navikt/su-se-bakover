package vilkår.skatt.application

import no.nav.su.se.bakover.dokument.infrastructure.client.KunneIkkeGenererePdf
import person.domain.KunneIkkeHentePerson

sealed interface KunneIkkeHenteOgLagePdfAvSkattegrunnlag {

    data class KunneIkkeHenteSkattemelding(val originalFeil: vilkår.skatt.domain.KunneIkkeHenteSkattemelding) :
        KunneIkkeHenteOgLagePdfAvSkattegrunnlag

    data class FeilVedHentingAvPerson(val originalFeil: KunneIkkeHentePerson) : KunneIkkeHenteOgLagePdfAvSkattegrunnlag
    data class FeilVedPdfGenerering(val originalFeil: KunneIkkeGenererePdf) :
        KunneIkkeHenteOgLagePdfAvSkattegrunnlag
}
