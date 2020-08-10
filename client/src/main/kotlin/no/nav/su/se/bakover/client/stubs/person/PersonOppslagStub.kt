package no.nav.su.se.bakover.client.stubs.person

import arrow.core.right
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person

object PersonOppslagStub :
    PersonOppslag {
    override fun person(fnr: Fnr) = Person(
        fnr = fnr,
        aktørId = AktørId("aktørid"),
        fornavn = "Tore",
        mellomnavn = "Johnas",
        etternavn = "Strømøy"
    ).right()

    override fun aktørId(fnr: Fnr) = AktørId("aktørid").right()
}
