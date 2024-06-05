package tilgangstyring.domain

import person.domain.KunneIkkeHentePerson

data class IkkeTilgangTilSak(val underliggende: KunneIkkeHentePerson)
