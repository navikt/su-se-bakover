package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.revurderes.vedtakSomRevurderesMånedsvis
import java.time.Clock

fun Sak.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(
    revurdering: RevurderingTilAttestering,
    clock: Clock,
): Either<DetHarKommetNyeOverlappendeVedtak, Unit> {
    val current = this.vedtakSomRevurderesMånedsvis(
        periode = revurdering.periode,
        clock = clock,
    ).getOrElse { throw IllegalStateException(it.toString()) }
    return if (current != revurdering.vedtakSomRevurderesMånedsvis) {
        DetHarKommetNyeOverlappendeVedtak.left()
    } else {
        Unit.right()
    }
}

object DetHarKommetNyeOverlappendeVedtak
