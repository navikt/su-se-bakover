package no.nav.su.se.bakover.domain.vedtak.brev

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
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

// TODO jah: Bør heller lage en abstract function på VedtakSomKanRevurderes. Da slipper vi when-else
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

        is VedtakInnvilgetRevurdering -> {
            /**
             * Vi har ikke et så godt skille mellom det som er forhåndsvisning av vedtaket, og det som faktisk er utsendingen.
             * Ved forhåndsvisning krever vi ikke noe fritekst, men vi gjør det når brevet skal sendes ut.
             */
            require(this.behandling.brevvalgRevurdering is BrevvalgRevurdering.Valgt.SendBrev && this.behandling.brevvalgRevurdering.fritekst != null) {
                "Generering av dokument for vedtaket forutsetter at vi skal sende brev, og at friteksten er satt"
            }
            this.behandling.lagDokumentKommando(
                satsFactory = satsFactory,
                clock = clock,
            )
        }

        is VedtakInnvilgetSøknadsbehandling -> this.behandling.lagBrevCommand(
            satsFactory = satsFactory,
        )

        is VedtakGjenopptakAvYtelse -> throw IllegalStateException("Skal ikke sende brev for VedtakGjenopptakAvYtelse")
        is VedtakInnvilgetRegulering -> throw IllegalStateException("Skal ikke sende brev for VedtakInnvilgetRegulering")
        is VedtakStansAvYtelse -> throw IllegalStateException("Skal ikke sende brev for VedtakStansAvYtelse")
        else -> throw java.lang.IllegalStateException("Ikke tatt høyde for ${this::class.simpleName} ved generering av dokumentkommando")
    }
}
