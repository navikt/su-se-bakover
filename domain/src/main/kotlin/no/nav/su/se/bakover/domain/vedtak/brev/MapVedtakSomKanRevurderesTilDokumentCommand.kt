package no.nav.su.se.bakover.domain.vedtak.brev

import no.nav.su.se.bakover.domain.brev.command.GenererDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørAvkorting
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import java.time.Clock

fun VedtakSomKanRevurderes.lagDokumentKommando(
    clock: Clock,
    satsFactory: SatsFactory,
): GenererDokumentCommand {
    return when (this) {
        is VedtakOpphørAvkorting -> this.behandling.lagDokumentKommando(
            clock = clock,
            satsFactory = satsFactory,
        )

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
