package no.nav.su.se.bakover.domain.avkorting

import java.util.UUID

interface AvkortingsvarselRepo {
    fun hentUtestående(sakId: UUID): Avkortingsvarsel
}
