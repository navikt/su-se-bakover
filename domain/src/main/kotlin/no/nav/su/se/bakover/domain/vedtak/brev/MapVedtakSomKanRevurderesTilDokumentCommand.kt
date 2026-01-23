package no.nav.su.se.bakover.domain.vedtak.brev

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørUtenUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import satser.domain.SatsFactory
import vedtak.domain.VedtakSomKanRevurderes
import java.time.Clock

fun VedtakSomKanRevurderes.lagDokumentKommando(
    clock: Clock,
    satsFactory: SatsFactory,
    fritekst: String,
): GenererDokumentCommand {
    return when (this) {
        is VedtakOpphørUtenUtbetaling -> fail("Historisk vedtak. Skal ikke sende nye brev for VedtakOpphørUtenUtbetaling.")

        is VedtakOpphørMedUtbetaling -> this.behandling.lagDokumentKommando(
            clock = clock,
            satsFactory = satsFactory,
            fritekst = fritekst,
        )

        is VedtakInnvilgetRevurdering -> {
            this.behandling.lagDokumentKommando(
                satsFactory = satsFactory,
                clock = clock,
                fritekst = fritekst,
            )
        }

        is VedtakInnvilgetSøknadsbehandling -> this.behandling.lagBrevCommand(
            satsFactory = satsFactory,
            fritekst = fritekst,
        )

        is VedtakGjenopptakAvYtelse -> fail("Skal ikke sende brev for VedtakGjenopptakAvYtelse")
        is VedtakInnvilgetRegulering -> fail("Skal ikke sende brev for VedtakInnvilgetRegulering")
        is VedtakStansAvYtelse -> fail("Skal ikke sende brev for VedtakStansAvYtelse")
        else -> fail("Ikke tatt høyde for ${this::class.simpleName} ved generering av dokumentkommando")
    }
}

internal fun VedtakSomKanRevurderes.fail(message: String): Nothing {
    val contextMessage = "Vedtakid: ${this.id}, Sakid: ${this.sakId}. $message"
    throw IllegalStateException(contextMessage)
}
