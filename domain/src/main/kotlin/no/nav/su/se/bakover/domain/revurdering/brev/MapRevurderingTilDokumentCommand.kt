package no.nav.su.se.bakover.domain.revurdering.brev

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.ident.NavIdentBruker
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
    fritekst: String,
): GenererDokumentCommand {
    return when (this) {
        is OpprettetRevurdering -> throw IllegalArgumentException("Kan ikke lage brevutkast for opprettet revurdering ${this.id}")
        is BeregnetRevurdering -> throw IllegalArgumentException("Kan ikke lage brevutkast for beregnet revurdering ${this.id}")
        is SimulertRevurdering.Innvilget -> lagKommando(satsFactory, fritekst)
        is SimulertRevurdering.Opphørt -> opphør(satsFactory, clock, fritekst)
        is RevurderingTilAttestering.Innvilget -> lagKommando(satsFactory, fritekst)
        is RevurderingTilAttestering.Opphørt -> opphør(satsFactory, clock, fritekst)
        is IverksattRevurdering.Innvilget -> lagKommando(satsFactory, fritekst)
        is IverksattRevurdering.Opphørt -> opphør(satsFactory, clock, fritekst)
        is UnderkjentRevurdering.Innvilget -> lagKommando(satsFactory, fritekst)
        is UnderkjentRevurdering.Opphørt -> opphør(satsFactory, clock, fritekst)
        is AvsluttetRevurdering -> {
            val avsluttetSaksbehandler = this.avsluttetAv as? NavIdentBruker.Saksbehandler ?: error(
                "AvsluttetRevurdering.avsluttetAv må være NavIdenBruker.Saksbehandler, men var " +
                    (this.avsluttetAv?.let { it::class.simpleName } ?: "null"),
            )
            AvsluttRevurderingDokumentCommand(
                fødselsnummer = this.fnr,
                saksnummer = this.saksnummer,
                sakstype = this.sakstype,
                saksbehandler = avsluttetSaksbehandler,
                fritekst = fritekst,
            )
        }
    }
}

private fun Revurdering.lagKommando(satsFactory: SatsFactory, fritekst: String): IverksettRevurderingDokumentCommand {
    return lagRevurderingInntektDokumentKommando(
        revurdering = this,
        // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
        beregning = this.beregning!!,
        satsFactory = satsFactory,
        fritekst = fritekst,
    )
}

private fun Revurdering.opphør(
    satsFactory: SatsFactory,
    clock: Clock,
    fritekst: String,
): IverksettRevurderingDokumentCommand {
    return lagRevurderingOpphørtDokumentKommando(
        revurdering = this,
        // TODO jah: Dette kan løses med et ekstra interface på revurderingstypene. Da kan vi fjerne null sjekken.
        beregning = this.beregning!!,
        satsFactory = satsFactory,
        clock = clock,
        fritekst = fritekst,
    )
}
