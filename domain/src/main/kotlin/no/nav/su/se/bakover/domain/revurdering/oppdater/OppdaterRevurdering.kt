package no.nav.su.se.bakover.domain.revurdering.oppdater

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import java.time.Clock

fun Sak.oppdaterRevurdering(
    command: OppdaterRevurderingCommand,
    clock: Clock,
): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
    val revurderingsårsak = command.revurderingsårsak.getOrElse {
        return when (it) {
            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> {
                KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse
            }

            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> {
                KunneIkkeOppdatereRevurdering.UgyldigÅrsak
            }
        }.left()
    }

    if (revurderingsårsak.årsak.erOmgjøring()) {
        if (!command.omgjøringsgrunnErGyldig()) {
            return KunneIkkeOppdatereRevurdering.MåhaOmgjøringsgrunn.left()
        }
    }
    val informasjonSomRevurderes = InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(
        sakstype = this.type,
        revurderingsteg = command.informasjonSomRevurderes,
    ).getOrElse {
        return KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
    }
    val revurdering = hentRevurdering(command.revurderingId).fold(
        { throw IllegalArgumentException("Fant ikke revurdering med id ${command.revurderingId}") },
        {
            if (it is Revurdering) it else throw IllegalArgumentException("Revurdering med id ${command.revurderingId} var av feil type: ${it::class.qualifiedName}")
        },
    )
    val periode = command.periode

    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = periode,
        clock = clock,
    ).getOrElse {
        return KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(it).left()
    }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata.vilkårsvurderinger)
        .getOrElse { return KunneIkkeOppdatereRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    return revurdering.oppdater(
        clock = clock,
        periode = periode,
        revurderingsårsak = revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
        tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = periode.fraOgMed)!!.id,
        saksbehandler = command.saksbehandler,
    )
}
