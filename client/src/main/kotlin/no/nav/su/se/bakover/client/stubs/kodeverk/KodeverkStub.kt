package no.nav.su.se.bakover.client.stubs.kodeverk

import arrow.core.right
import no.nav.su.se.bakover.client.kodeverk.Kodeverk

object KodeverkStub : Kodeverk {
    override fun hentPoststed(postnummer: String) = "OSLO".right()

    override fun hentKommunenavn(kommunenummer: String) = "OSLO".right()
}
