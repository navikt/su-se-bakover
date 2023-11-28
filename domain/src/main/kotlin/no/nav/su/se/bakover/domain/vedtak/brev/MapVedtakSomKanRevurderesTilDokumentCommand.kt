package no.nav.su.se.bakover.domain.vedtak.brev

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørUtenUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import satser.domain.SatsFactory
import java.time.Clock

fun VedtakSomKanRevurderes.lagDokumentKommando(
    clock: Clock,
    satsFactory: SatsFactory,
): GenererDokumentCommand {
    return when (this) {
        is VedtakOpphørUtenUtbetaling -> throw IllegalStateException("Historisk vedtak. Skal ikke sende nye brev for VedtakOpphørUtenUtbetaling.")

        is VedtakOpphørMedUtbetaling -> this.behandling.lagDokumentKommando(
            clock = clock,
            satsFactory = satsFactory,
        )

        is VedtakInnvilgetRevurdering -> this.behandling.lagDokumentKommando(
            satsFactory = satsFactory,
            clock = clock,
        )

        is VedtakInnvilgetSøknadsbehandling -> this.behandling.lagBrevCommand(
            satsFactory = satsFactory,
        )

        is VedtakGjenopptakAvYtelse -> throw IllegalStateException("Skal ikke sende brev for VedtakGjenopptakAvYtelse")
        is VedtakInnvilgetRegulering -> throw IllegalStateException("Skal ikke sende brev for VedtakInnvilgetRegulering")
        is VedtakStansAvYtelse -> throw IllegalStateException("Skal ikke sende brev for VedtakStansAvYtelse")
    }
}
