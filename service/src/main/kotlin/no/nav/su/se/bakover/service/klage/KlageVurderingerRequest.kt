package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.Hjemmel
import behandling.klage.domain.KlageId
import behandling.klage.domain.Klagehjemler
import behandling.klage.domain.VurderingerTilKlage
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage

/**
 * Prøver å unngå validering / mapping i web-laget så holder det til primitiver/objekter/arrays
 */
data class KlageVurderingerRequest(
    val klageId: KlageId,
    private val saksbehandler: NavIdentBruker.Saksbehandler,
    private val fritekstTilBrev: String?,
    private val omgjør: Omgjør?,
    private val oppretthold: Oppretthold?,
) {
    data class Omgjør(val årsak: String?, val begrunnelse: String?) {
        fun toDomain(): Either<KunneIkkeVurdereKlage, VurderingerTilKlage.Vedtaksvurdering> {
            return VurderingerTilKlage.Vedtaksvurdering.createOmgjør(
                årsak = årsak?.let { årsakToDomain(it) }?.getOrElse { return it.left() },
                begrunnelse = begrunnelse,
            ).right()
        }

        private fun årsakToDomain(årsak: String): Either<KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak, VurderingerTilKlage.Vedtaksvurdering.Årsak> {
            val årsak = VurderingerTilKlage.Vedtaksvurdering.Årsak.entries.find { it.name == årsak }
            return årsak?.right() ?: KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak.left()
        }
    }

    data class Oppretthold(val hjemler: List<String>, val klagenotat: String?) {
        fun toDomain(): Either<KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler, VurderingerTilKlage.Vedtaksvurdering> {
            return hjemmelToDomain(hjemler)
                .flatMap {
                    VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
                        hjemler = it,
                        klagenotat = klagenotat,
                    ).mapLeft {
                        KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler
                    }
                }
        }

        private fun hjemmelToDomain(hjemler: List<String>): Either<KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler, Klagehjemler> {
            return hjemler.map { hjemmelStr ->
                runCatching { enumValueOf<Hjemmel>(hjemmelStr) }.getOrElse {
                    return KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler.left()
                }
            }.let {
                if (it.isEmpty()) return Klagehjemler.empty().right()
                Klagehjemler.tryCreate(
                    it.toNonEmptyList(),
                ).getOrElse {
                    return KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler.left()
                }.right()
            }
        }
    }

    private fun toVedtaksvurdering(): Either<KunneIkkeVurdereKlage, VurderingerTilKlage.Vedtaksvurdering?> {
        return when {
            omgjør == null && oppretthold == null -> null.right()
            omgjør != null -> omgjør.toDomain()
            oppretthold != null -> oppretthold.toDomain()
            else -> throw IllegalStateException("Håndterer at ikke begge har lov til å være utfylt.")
        }
    }

    fun toDomain(): Either<KunneIkkeVurdereKlage, Domain> {
        if (omgjør != null && oppretthold != null) {
            return KunneIkkeVurdereKlage.KanIkkeVelgeBådeOmgjørOgOppretthold.left()
        }
        val fritekstTilBrev = if (oppretthold != null) {
            fritekstTilBrev
        } else {
            null
        }
        return Domain(
            klageId = klageId,
            vurderinger = VurderingerTilKlage.create(
                fritekstTilOversendelsesbrev = fritekstTilBrev,
                vedtaksvurdering = toVedtaksvurdering().getOrElse { return it.left() },
            ),
            saksbehandler = saksbehandler,
        ).right()
    }

    data class Domain(
        val klageId: KlageId,
        val vurderinger: VurderingerTilKlage,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )
}
