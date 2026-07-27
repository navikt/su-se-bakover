package no.nav.su.se.bakover.domain.notat

import java.util.UUID

interface VedleggRepo {
    fun leggTil(vedlegg: NotatVedlegg)
    fun slett(vedleggId: UUID)
    fun hent(vedleggId: UUID): NotatVedlegg?
    fun hentForNotat(notatId: UUID): List<NotatVedlegg>
    fun hentAntallVedlegg(notatId: UUID): Int
}
