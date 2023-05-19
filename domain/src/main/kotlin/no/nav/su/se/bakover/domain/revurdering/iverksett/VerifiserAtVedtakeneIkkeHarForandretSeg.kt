package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.revurderes.vedtakSomRevurderesMånedsvis
import java.time.Clock

fun Sak.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(
    revurdering: AbstraktRevurdering,
    clock: Clock,
): Either<DetHarKommetNyeOverlappendeVedtak, Unit> {
    return verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(
        periode = revurdering.periode,
        eksisterendeVedtakSomRevurderesMånedsvis = revurdering.vedtakSomRevurderesMånedsvis,
        clock = clock,
    )
}

fun Sak.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(
    periode: Periode,
    eksisterendeVedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
    clock: Clock,
): Either<DetHarKommetNyeOverlappendeVedtak, Unit> {
    val current = this.vedtakSomRevurderesMånedsvis(
        periode = periode,
        clock = clock,
    ).getOrElse { throw IllegalStateException(it.toString()) }
    return if (current != eksisterendeVedtakSomRevurderesMånedsvis) {
        DetHarKommetNyeOverlappendeVedtak.left()
    } else {
        Unit.right()
    }
}

object DetHarKommetNyeOverlappendeVedtak
