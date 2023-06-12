package no.nav.su.se.bakover.domain.revurdering.oppdater

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.avkorting.hentOgKontrollerUteståendeAvkorting
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
    val informasjonSomRevurderes = InformasjonSomRevurderes.tryCreate(
        revurderingsteg = command.informasjonSomRevurderes,
    ).getOrElse {
        return KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
    }
    val revurdering = hentRevurdering(command.revurderingId).fold(
        { throw IllegalArgumentException("Fant ikke revurdering med id ${command.revurderingId}") },
        {
            if (it is Revurdering) it else throw IllegalArgumentException("Revurdering med id ${command.revurderingId} var av feil type: ${it::class.simpleName}")
        },
    )
    val periode = command.periode

    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = periode,
        clock = clock,
    ).getOrElse {
        return KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(it).left()
    }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata)
        .getOrElse { return KunneIkkeOppdatereRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    val avkorting: AvkortingVedRevurdering.Uhåndtert = hentOgKontrollerUteståendeAvkorting(command.periode).getOrElse {
        return KunneIkkeOppdatereRevurdering.Avkorting(it).left()
    }

    return revurdering.oppdater(
        clock = clock,
        periode = periode,
        revurderingsårsak = revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
        tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = periode.fraOgMed)!!.id,
        avkorting = avkorting,
        saksbehandler = command.saksbehandler,
    )
}
