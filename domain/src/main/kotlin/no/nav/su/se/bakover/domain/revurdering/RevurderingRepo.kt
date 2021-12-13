package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.behandling.Attestering
import java.util.UUID

interface RevurderingRepo {
    fun hent(id: UUID): AbstraktRevurdering?
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
    fun lagre(revurdering: AbstraktRevurdering)
    fun oppdaterForhåndsvarsel(id: UUID, forhåndsvarsel: Forhåndsvarsel)
}
