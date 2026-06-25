package no.nav.su.se.bakover.domain.notat

import java.util.UUID

interface NotatRepo {
    fun opprett(notat: Notat)
    fun oppdater(notat: Notat)
    fun hent(notatId: UUID): Notat?
    fun hentForSak(sakId: UUID): List<Notat>
    fun eksistererForReferanse(sakId: UUID, referanseId: UUID): Boolean
}
