package no.nav.su.se.bakover.client.stubs

import no.nav.su.person.sts.TokenOppslag

object TokenOppslagStub : TokenOppslag {
    override fun token() = "token"
}
