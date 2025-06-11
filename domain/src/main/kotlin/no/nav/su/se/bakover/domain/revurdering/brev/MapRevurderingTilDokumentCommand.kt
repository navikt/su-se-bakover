package no.nav.su.se.bakover.domain.revurdering.brev

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand
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
import satser.domain.SatsFactory
import java.time.Clock

fun Revurdering.lagDokumentKommando(
    satsFactory: SatsFactory,
    clock: Clock,
): GenererDokumentCommand {
    return when (this) {
        is OpprettetRevurdering -> throw IllegalArgumentException("Kan ikke lage brevutkast for opprettet revurdering ${this.id}")
        is BeregnetRevurdering -> throw IllegalArgumentException("Kan ikke lage brevutkast for beregnet revurdering ${this.id}")
        is SimulertRevurdering.Innvilget -> lagKommando(satsFactory)
        is SimulertRevurdering.Opphørt -> opphør(satsFactory, clock)
        is RevurderingTilAttestering.Innvilget -> lagKommando(satsFactory)
        is RevurderingTilAttestering.Opphørt -> opphør(satsFactory, clock)
        is IverksattRevurdering.Innvilget -> lagKommando(satsFactory)
        is IverksattRevurdering.Opphørt -> opphør(satsFactory, clock)
        is UnderkjentRevurdering.Innvilget -> lagKommando(satsFactory)
        is UnderkjentRevurdering.Opphørt -> opphør(satsFactory, clock)
        is AvsluttetRevurdering -> AvsluttRevurderingDokumentCommand(
            fødselsnummer = this.fnr,
            saksnummer = this.saksnummer,
            sakstype = this.sakstype,
            saksbehandler = saksbehandler,
            fritekst = this.brevvalg.fritekst,
        )
    }
}

private fun Revurdering.lagKommando(satsFactory: SatsFactory): IverksettRevurderingDokumentCommand {
    return lagRevurderingInntektDokumentKommando(
        revurdering = this,
        // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
        beregning = this.beregning!!,
        satsFactory = satsFactory,
    )
}

private fun Revurdering.opphør(
    satsFactory: SatsFactory,
    clock: Clock,
): IverksettRevurderingDokumentCommand {
    return lagRevurderingOpphørtDokumentKommando(
        revurdering = this,
        // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
        beregning = this.beregning!!,
        satsFactory = satsFactory,
        clock = clock,
    )
}
