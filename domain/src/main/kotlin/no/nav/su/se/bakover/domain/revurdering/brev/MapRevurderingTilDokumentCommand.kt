package no.nav.su.se.bakover.domain.revurdering.brev

import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.GenererDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.endringInntekt.lagRevurderingInntektDokumentKommando
import no.nav.su.se.bakover.domain.revurdering.brev.opphør.lagRevurderingOpphørtDokumentKommando
import no.nav.su.se.bakover.domain.revurdering.brev.tilbakekreving.lagTilbakekrevingDokumentKommando
import no.nav.su.se.bakover.domain.satser.SatsFactory
import java.time.Clock

fun Revurdering.lagDokumentKommando(
    satsFactory: SatsFactory,
    clock: Clock,
): GenererDokumentCommand {
    if (!this.skalSendeVedtaksbrev()) {
        throw IllegalArgumentException("Kan ikke lage brevutkast for revurdering ${this.id} siden brevvalget tilsier at det ikke skal sendes brev.")
    }
    return when (this) {
        is OpprettetRevurdering -> throw IllegalArgumentException("Kan ikke lage brevutkast for opprettet revurdering ${this.id}")
        is BeregnetRevurdering -> throw IllegalArgumentException("Kan ikke lage brevutkast for beregnet revurdering ${this.id}")
        is SimulertRevurdering.Innvilget -> inntektMedEllerUtenTilbakekreving(satsFactory)
        is SimulertRevurdering.Opphørt -> opphør(satsFactory, clock)
        is RevurderingTilAttestering.Innvilget -> inntektMedEllerUtenTilbakekreving(satsFactory)
        is RevurderingTilAttestering.Opphørt -> opphør(satsFactory, clock)
        is IverksattRevurdering.Innvilget -> inntektMedEllerUtenTilbakekreving(satsFactory)
        is IverksattRevurdering.Opphørt -> opphør(satsFactory, clock)
        is UnderkjentRevurdering.Innvilget -> inntektMedEllerUtenTilbakekreving(satsFactory)
        is UnderkjentRevurdering.Opphørt -> opphør(satsFactory, clock)
        is AvsluttetRevurdering -> {
            if (!this.brevvalg.skalSendeBrev()) {
                throw IllegalArgumentException("Kan ikke lage brev for avsluttet revurdering ${this.id} siden brevvalget tilsier at det ikke skal sendes brev.")
            }
            AvsluttRevurderingDokumentCommand(
                fødselsnummer = this.fnr,
                saksnummer = this.saksnummer,
                saksbehandler = saksbehandler,
                fritekst = this.brevvalg.fritekst,
            )
        }
    }
}

private fun Revurdering.inntektMedEllerUtenTilbakekreving(satsFactory: SatsFactory): IverksettRevurderingDokumentCommand {
    return if (this.skalTilbakekreve()) {
        lagTilbakekrevingDokumentKommando(
            revurdering = this,
            // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
            beregning = this.beregning!!,
            simulering = this.simulering!!,
            satsFactory = satsFactory,
        )
    } else {
        lagRevurderingInntektDokumentKommando(
            revurdering = this,
            // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
            beregning = this.beregning!!,
            satsFactory = satsFactory,
        )
    }
}

private fun Revurdering.opphør(
    satsFactory: SatsFactory,
    clock: Clock,
): IverksettRevurderingDokumentCommand {
    return if (this.skalTilbakekreve()) {
        lagTilbakekrevingDokumentKommando(
            revurdering = this,
            // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
            beregning = this.beregning!!,
            // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
            simulering = this.simulering!!,
            satsFactory = satsFactory,
        )
    } else {
        lagRevurderingOpphørtDokumentKommando(
            revurdering = this,
            // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
            beregning = this.beregning!!,
            satsFactory = satsFactory,
            clock = clock,
        )
    }
}
