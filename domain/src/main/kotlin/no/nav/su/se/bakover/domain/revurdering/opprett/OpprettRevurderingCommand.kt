package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import java.util.UUID
import kotlin.enums.enumEntries

data class OpprettRevurderingCommand(
    val sakId: UUID,
    val periode: Periode,
    private val årsak: String,
    private val begrunnelse: String,
    val omgjøringsgrunn: String? = null,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val informasjonSomRevurderes: List<Revurderingsteg>,
) {
    val revurderingsårsak: Either<Revurderingsårsak.UgyldigRevurderingsårsak, Revurderingsårsak> by lazy {
        Revurderingsårsak.tryCreate(
            årsak = årsak,
            begrunnelse = begrunnelse,
        ).flatMap {
            if (it.årsak == Revurderingsårsak.Årsak.MIGRERT) {
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak.left()
            } else {
                it.right()
            }
        }
    }

    fun omgjøringsgrunnErGyldig(): Boolean {
        return enumEntries<Omgjøringsgrunn>().any { it.name == omgjøringsgrunn }
    }
}
