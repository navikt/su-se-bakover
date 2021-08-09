package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

val revurderingId: UUID = UUID.randomUUID()

val oppgaveIdRevurdering = OppgaveId("oppgaveIdRevurdering")

/** MELDING_FRA_BRUKER */
val revurderingsårsak =
    Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("revurderingsårsakBegrunnelse"),
    )

/**
 * En revurdering i sin tidligste tilstand der den er basert på et innvilget søknadsbehandlingsvedtak
 * Arver grunnlagsdata/vilkårsvurderinger med samme periode som stønadsperioden - TODO jah: Støtte truncating (bruk en domeneklasse/factory til dette)
 *
 * Defaults:
 * - jan til des 2021
 * - Uten fradrag
 * - Enslig ektefelle
 * - Årsak: Melding fra bruker
 */
fun opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
    ),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = tilRevurdering.behandling.vilkårsvurderinger,
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak
): OpprettetRevurdering {
    require(stønadsperiode.periode == revurderingsperiode && revurderingsperiode == tilRevurdering.periode) {
        "En foreløpig begrensning for å bruke testfunksjonen opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak er at stønadsperiode == revurderingsperiode. stønadsperiode=${stønadsperiode.periode}, Revurderingsperiode=$revurderingsperiode, tilRevurdering's periode=${tilRevurdering.periode}"
    }
    return OpprettetRevurdering(
        id = revurderingId,
        periode = revurderingsperiode,
        opprettet = fixedTidspunkt,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveIdRevurdering,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty()
    )
}

/**
 * En innvilget beregnet revurdering med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 */
fun beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
    ),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = tilRevurdering.behandling.vilkårsvurderinger,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak
): BeregnetRevurdering.Innvilget {

    require(stønadsperiode.periode == revurderingsperiode && revurderingsperiode == tilRevurdering.periode) {
        "En foreløpig begrensning for å bruke testfunksjonen opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak er at stønadsperiode == revurderingsperiode. stønadsperiode=${stønadsperiode.periode}, Revurderingsperiode=$revurderingsperiode, tilRevurdering's periode=${tilRevurdering.periode}"
    }

    return opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).beregn(eksisterendeUtbetalinger).orNull() as BeregnetRevurdering.Innvilget
}

/**
 * En beregnet revurdering som gir opphør med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 *
 * Opphører både på formue+uføre-vilkår
 */
fun beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
    ),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerAvslåttUføre(
        periode = stønadsperiode.periode,
        bosituasjon = tilRevurdering.behandling.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
    ),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak
): BeregnetRevurdering.Opphørt {

    require(stønadsperiode.periode == revurderingsperiode && revurderingsperiode == tilRevurdering.periode) {
        "En foreløpig begrensning for å bruke testfunksjonen opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak er at stønadsperiode == revurderingsperiode. stønadsperiode=${stønadsperiode.periode}, Revurderingsperiode=$revurderingsperiode, tilRevurdering's periode=${tilRevurdering.periode}"
    }

    return opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).beregn(eksisterendeUtbetalinger).getOrHandle { throw IllegalStateException("Kunne ikke instansiere testdata. Underliggende feil: $it") } as BeregnetRevurdering.Opphørt
}

/**
 * En innvilget simulert revurdering med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 */
fun simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
    ),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = tilRevurdering.behandling.vilkårsvurderinger,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak
): SimulertRevurdering.Innvilget {

    require(stønadsperiode.periode == revurderingsperiode && revurderingsperiode == tilRevurdering.periode) {
        "En foreløpig begrensning for å bruke testfunksjonen opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak er at stønadsperiode == revurderingsperiode. stønadsperiode=${stønadsperiode.periode}, Revurderingsperiode=$revurderingsperiode, tilRevurdering's periode=${tilRevurdering.periode}"
    }

    return beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        revurderingsårsak = revurderingsårsak,
    ).let {
        it.toSimulert(
            simuleringNy(
                beregning = it.beregning,
                eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                fnr = it.fnr,
                sakId = it.sakId,
                saksnummer = it.saksnummer,
            ),
        )
    }
}

fun simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
    ),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerAvslåttUføre(
        periode = stønadsperiode.periode,
        bosituasjon = tilRevurdering.behandling.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
    ),
    /** Krever minst en utbetaling som man kan opphøre*/
    eksisterendeUtbetalinger: NonEmptyList<Utbetaling> = nonEmptyListOf(
        oversendtUtbetalingUtenKvittering(
            periode = stønadsperiode.periode,
        ),
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak
): SimulertRevurdering.Opphørt {

    require(stønadsperiode.periode == revurderingsperiode && revurderingsperiode == tilRevurdering.periode) {
        "En foreløpig begrensning for å bruke testfunksjonen opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak er at stønadsperiode == revurderingsperiode. stønadsperiode=${stønadsperiode.periode}, Revurderingsperiode=$revurderingsperiode, tilRevurdering's periode=${tilRevurdering.periode}"
    }

    return beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        revurderingsårsak = revurderingsårsak,
    ).let {
        it.toSimulert(
            simuleringOpphørt(
                opphørsdato = it.periode.fraOgMed,
                eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                fnr = it.fnr,
                sakId = it.sakId,
                saksnummer = it.saksnummer,
            ),
        )
    }
}

fun tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
    ),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = tilRevurdering.behandling.vilkårsvurderinger,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak
): RevurderingTilAttestering.Innvilget {
    return simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        revurderingsårsak = revurderingsårsak,
    ).tilAttestering(
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        forhåndsvarsel = forhåndsvarsel,
    )
}

fun UnderkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(saksnr = saksnr, stønadsperiode = stønadsperiode),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = tilRevurdering.behandling.vilkårsvurderinger,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    attestering: Attestering.Underkjent = Attestering.Underkjent(
        attestant = NavIdentBruker.Attestant(navIdent = "attestant"),
        grunn = Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT,
        kommentar = "feil vilkår man",
        opprettet = fixedTidspunkt
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): UnderkjentRevurdering {
    return tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        forhåndsvarsel = forhåndsvarsel,
        revurderingsårsak = revurderingsårsak,
    ).underkjenn(
        attestering = attestering,
        oppgaveId = OppgaveId(value = "oppgaveId")
    )
}

fun IverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    tilRevurdering: VedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(saksnr = saksnr, stønadsperiode = stønadsperiode),
    // TODO jah: Grunnlagsdata/vilkårsvurderinger bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby på en enkel måte, uten å gå via en service.
    grunnlagsdata: Grunnlagsdata = tilRevurdering.behandling.grunnlagsdata,
    vilkårsvurderinger: Vilkårsvurderinger = tilRevurdering.behandling.vilkårsvurderinger,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("Attestant"),
    utbetal: () -> Either<RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30> = { UUID30.randomUUID().right() },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): IverksattRevurdering.Innvilget {
    return tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        forhåndsvarsel = forhåndsvarsel,
        revurderingsårsak = revurderingsårsak,
    ).tilIverksatt(
        attestant = attestant,
        utbetal = utbetal,
    ).getOrHandle { throw RuntimeException("Feilet med generering av test data for Iverksatt-revurdering") }
}
