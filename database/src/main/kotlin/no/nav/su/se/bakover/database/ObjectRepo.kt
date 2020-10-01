package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak

interface ObjectRepo {
    fun opprettSak(fnr: Fnr): Sak
}
