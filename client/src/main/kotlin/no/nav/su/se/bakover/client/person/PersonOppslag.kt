package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.domain.Fnr

interface PersonOppslag {
    fun person(ident: Fnr): ClientResponse
    fun aktørId(ident: Fnr): String
}
