package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.routes.sak.sakPath
import java.util.UUID

object RevurderingRoutesTestData {

    internal val sakId = UUID.randomUUID()
    internal val requestPath = "$sakPath/$sakId/revurderinger"
    internal val testServices = TestServicesBuilder.services()
    internal val periode = år(2021)

    internal val vedtak = vedtakSøknadsbehandlingIverksattInnvilget().second

    internal val opprettetRevurdering = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = periode,
        opprettet = fixedTidspunkt,
        tilRevurdering = vedtak.id,
        saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
        oppgaveId = OppgaveId("oppgaveid"),
        fritekstTilBrev = "",
        revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
            Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
        ),
        forhåndsvarsel = null,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
        sakinfo = vedtak.sakinfo(),
    )
}
