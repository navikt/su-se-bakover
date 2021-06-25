package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import java.util.UUID

val revurderingId: UUID = UUID.randomUUID()

val oppgaveIdRevurdering = OppgaveId("oppgaveIdRevurdering")

val revurderingsårsak =
    Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("revurderingsårsakBegrunnelse"),
    )

/**
 * En revurdering i sin tidligste tilstand der den er basert på et innvilget søknadsbehandlingsvedtak
 *
 * - Uten fradrag
 * - Enslig ektefelle
 * - Årsak: Melding fra bruker
 */
fun opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
    // TODO jah: Sjekke at revurderingsperioden inneholder stønadsperioden?
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
): OpprettetRevurdering {
    val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = stønadsperiode)
    return OpprettetRevurdering(
        id = revurderingId,
        periode = revurderingsperiode,
        opprettet = fixedTidspunkt,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveIdRevurdering,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        grunnlagsdata = tilRevurdering.behandling.grunnlagsdata, // TODO jah: Bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby.
        vilkårsvurderinger = tilRevurdering.behandling.vilkårsvurderinger, // TODO jah: Bør truncates basert på revurderingsperioden. Dette er noe domenemodellen bør tilby.
        informasjonSomRevurderes = informasjonSomRevurderes,
    )
}
