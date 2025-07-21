package no.nav.su.se.bakover.domain.revurdering.oppdater

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import kotlin.enums.enumEntries

data class OppdaterRevurderingCommand(
    val revurderingId: RevurderingId,
    val periode: Periode,
    val årsak: String,
    val begrunnelse: String,
    val omgjøringsgrunn: String? = null,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val informasjonSomRevurderes: List<Revurderingsteg>,
) {
    val revurderingsårsak = Revurderingsårsak.tryCreate(
        årsak = årsak,
        begrunnelse = begrunnelse,
    )
    fun omgjøringsgrunnErGyldig(): Boolean {
        return enumEntries<Omgjøringsgrunn>().any { it.name == omgjøringsgrunn }
    }
}
