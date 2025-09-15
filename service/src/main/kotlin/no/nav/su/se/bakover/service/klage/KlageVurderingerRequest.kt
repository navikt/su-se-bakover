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
    data class Omgjør(val årsak: String?, val utfall: String?) {
        fun toDomain(): Either<KunneIkkeVurdereKlage, VurderingerTilKlage.Vedtaksvurdering> {
            return VurderingerTilKlage.Vedtaksvurdering.createOmgjør(
                årsak = årsak?.let { årsakToDomain(it) }?.getOrElse { return it.left() },
                utfall = utfall?.let { utfallToDomain(it) }?.getOrElse { return it.left() },
            ).right()
        }

        private fun årsakToDomain(årsak: String): Either<KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak, VurderingerTilKlage.Vedtaksvurdering.Årsak> {
            return when (årsak) {
                "NYE_OPPLYSNINGER" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.NYE_OPPLYSNINGER
                "FEIL_LOVANVENDELSE" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE
                "FEIL_REGELFORSTÅELSE" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_REGELFORSTÅELSE
                "FEIL_FAKTUM" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_FAKTUM
                else -> return KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak.left()
            }.right()
        }

        private fun utfallToDomain(utfall: String): Either<KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall, VurderingerTilKlage.Vedtaksvurdering.Utfall> {
            return when (utfall) {
                "TIL_GUNST" -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST
                "TIL_UGUNST" -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST
                else -> return KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall.left()
            }.right()
        }
    }

    data class Oppretthold(val hjemler: List<String>) {

        fun toDomain(): Either<KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler, VurderingerTilKlage.Vedtaksvurdering> {
            return hjemmelToDomain(hjemler)
                .flatMap {
                    VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
                        hjemler = it,
                    ).mapLeft {
                        KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler
                    }
                }
        }

        private fun hjemmelToDomain(hjemler: List<String>): Either<KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler, Klagehjemler> {
            return hjemler.map { hjemmel ->
                when (hjemmel) {
                    "SU_PARAGRAF_3" -> Hjemmel.SU_PARAGRAF_3
                    "SU_PARAGRAF_4" -> Hjemmel.SU_PARAGRAF_4
                    "SU_PARAGRAF_5" -> Hjemmel.SU_PARAGRAF_5
                    "SU_PARAGRAF_6" -> Hjemmel.SU_PARAGRAF_6
                    "SU_PARAGRAF_7" -> Hjemmel.SU_PARAGRAF_7
                    "SU_PARAGRAF_8" -> Hjemmel.SU_PARAGRAF_8
                    "SU_PARAGRAF_9" -> Hjemmel.SU_PARAGRAF_9
                    "SU_PARAGRAF_10" -> Hjemmel.SU_PARAGRAF_10
                    "SU_PARAGRAF_11" -> Hjemmel.SU_PARAGRAF_11
                    "SU_PARAGRAF_12" -> Hjemmel.SU_PARAGRAF_12
                    "SU_PARAGRAF_13" -> Hjemmel.SU_PARAGRAF_13
                    "SU_PARAGRAF_17" -> Hjemmel.SU_PARAGRAF_17
                    "SU_PARAGRAF_18" -> Hjemmel.SU_PARAGRAF_18
                    "SU_PARAGRAF_21" -> Hjemmel.SU_PARAGRAF_21
                    else -> return KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler.left()
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
