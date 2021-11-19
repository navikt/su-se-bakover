package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import java.util.UUID

/**
 * Prøver å unngå validering / mapping i web-laget så holder det til primitiver/objekter/arrays
 */
data class KlageVurderingerRequest(
    val klageId: String,
    private val navIdent: String,
    private val fritekstTilBrev: String?,
    private val omgjør: Omgjør?,
    private val oppretthold: Oppretthold?,
) {
    data class Omgjør(val årsak: String?, val utfall: String?) {

        private fun erUtfylt(): Boolean = this.årsak != null && this.utfall != null

        fun toDomain(): Either<KunneIkkeVurdereKlage, VurderingerTilKlage.Vedtaksvurdering> {
            return if (erUtfylt()) {
                VurderingerTilKlage.Vedtaksvurdering.Utfylt.Omgjør(
                    årsak = årsakToDomain(årsak!!).getOrHandle { return it.left() },
                    utfall = utfallToDomain(utfall!!).getOrHandle { return it.left() },
                )
            } else {
                VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Omgjør(
                    årsak = årsak?.let { årsakToDomain(it) }?.getOrHandle { return it.left() },
                    utfall = utfall?.let { utfallToDomain(it) }?.getOrHandle { return it.left() },
                )
            }.right()
        }

        private fun årsakToDomain(årsak: String): Either<KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak, VurderingerTilKlage.Vedtaksvurdering.Årsak> {
            // TODO jah: Flytt denne kontrakten til web?
            return when (årsak) {
                "FEIL_LOVANVENDELSE" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE
                "ULIK_SKJØNNSVURDERING" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.ULIK_SKJØNNSVURDERING
                "SAKSBEHANDLINGSFEIL" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.SAKSBEHANDLINGSFEIL
                "NYTT_FAKTUM" -> VurderingerTilKlage.Vedtaksvurdering.Årsak.NYTT_FAKTUM
                else -> return KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak.left()
            }.right()
        }

        private fun utfallToDomain(utfall: String): Either<KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall, VurderingerTilKlage.Vedtaksvurdering.Utfall> {
            // TODO jah: Flytt denne kontrakten til web?
            return when (utfall) {
                "TIL_GUNST" -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST
                "TIL_UGUNST" -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST
                else -> return KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall.left()
            }.right()
        }
    }

    data class Oppretthold(val hjemler: List<String>) {

        fun toDomain(): Either<KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler, VurderingerTilKlage.Vedtaksvurdering> {
            return hjemmelToDomain(hjemler).map {
                when (it) {
                    is Hjemler.IkkeUtfylt -> VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold(
                        hjemler = it,
                    )
                    is Hjemler.Utfylt -> VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Oppretthold(
                        hjemler = it,
                    )
                }
            }
        }

        private fun hjemmelToDomain(hjemler: List<String>): Either<KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler, Hjemler> {
            return hjemler.map { hjemmel ->
                when (hjemmel) {
                    // TODO jah: Flytt denne kontrakten til web?
                    "SU_PARAGRAF_3" -> Hjemmel.SU_PARAGRAF_3
                    "SU_PARAGRAF_4" -> Hjemmel.SU_PARAGRAF_4
                    "SU_PARAGRAF_5" -> Hjemmel.SU_PARAGRAF_5
                    "SU_PARAGRAF_6" -> Hjemmel.SU_PARAGRAF_6
                    "SU_PARAGRAF_8" -> Hjemmel.SU_PARAGRAF_8
                    "SU_PARAGRAF_9" -> Hjemmel.SU_PARAGRAF_9
                    "SU_PARAGRAF_10" -> Hjemmel.SU_PARAGRAF_10
                    "SU_PARAGRAF_12" -> Hjemmel.SU_PARAGRAF_12
                    "SU_PARAGRAF_13" -> Hjemmel.SU_PARAGRAF_13
                    "SU_PARAGRAF_18" -> Hjemmel.SU_PARAGRAF_18
                    else -> return KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler.left()
                }
            }.let {
                if (it.isEmpty()) {
                    Hjemler.IkkeUtfylt.create()
                } else {
                    Hjemler.Utfylt.tryCreate(NonEmptyList.fromListUnsafe(it)).getOrHandle {
                        return KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler.left()
                    }
                }.right()
            }
        }
    }

    private fun erUtfylt(): Boolean {
        if (omgjør != null && oppretthold != null) throw IllegalStateException("Håndterer at ikke begge har lov til å være utfylt tidligere.")
        return fritekstTilBrev != null && (omgjør == null).xor(oppretthold == null)
    }

    private fun toVedtaksvurdering(): Either<KunneIkkeVurdereKlage, VurderingerTilKlage.Vedtaksvurdering?> {
        return when {
            omgjør == null && oppretthold == null -> null.right()
            omgjør != null -> omgjør.toDomain()
            oppretthold != null -> oppretthold.toDomain()
            else -> throw IllegalStateException("Håndterer at ikke begge har lov til å være utfylt tidligere.")
        }
    }

    private fun toUtfyltVedtaksvurdering(): Either<KunneIkkeVurdereKlage, VurderingerTilKlage.Vedtaksvurdering.Utfylt> {
        return toVedtaksvurdering().map {
            it!! as VurderingerTilKlage.Vedtaksvurdering.Utfylt
        }
    }

    fun toDomain(): Either<KunneIkkeVurdereKlage, Domain> {
        if (omgjør != null && oppretthold != null) {
            return KunneIkkeVurdereKlage.KanIkkeVelgeBådeOmgjørOgOppretthold.left()
        }
        return Domain(
            klageId = Either.catch { UUID.fromString(klageId) }
                .getOrElse { return KunneIkkeVurdereKlage.FantIkkeKlage.left() },
            vurderinger = if (erUtfylt()) {
                VurderingerTilKlage.Utfylt(
                    fritekstTilBrev = fritekstTilBrev!!,
                    vedtaksvurdering = toUtfyltVedtaksvurdering().getOrHandle { return it.left() },
                )
            } else {
                VurderingerTilKlage.Påbegynt(
                    fritekstTilBrev = fritekstTilBrev,
                    vedtaksvurdering = toVedtaksvurdering().getOrHandle { return it.left() },
                )
            },
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent)
        ).right()
    }

    data class Domain(
        val klageId: UUID,
        val vurderinger: VurderingerTilKlage,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )
}
