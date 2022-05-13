package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.stønadsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.TestBeregning
import org.mockito.kotlin.mock
import java.util.UUID

object RevurderingRoutesTestData {

    internal val sakId = UUID.randomUUID()
    internal val requestPath = "$sakPath/$sakId/revurderinger"
    internal val testServices = TestServicesBuilder.services()
    internal val periode = år(2021)

    internal val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(
        søknadsbehandling = Søknadsbehandling.Iverksatt.Innvilget(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                journalpostId = JournalpostId(value = ""),
                oppgaveId = OppgaveId(value = ""),

            ),
            oppgaveId = OppgaveId(value = ""),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = Fnr.generer(),
            beregning = TestBeregning,
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saks"),
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"), fixedTidspunkt)),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            avkorting = AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående,
        ),
        utbetalingId = UUID30.randomUUID(),
        clock = fixedClock,
    )

    internal val opprettetRevurdering = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = periode,
        opprettet = fixedTidspunkt,
        tilRevurdering = vedtak,
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
    )

    internal fun formueVilkår(periode: Periode) = Vilkår.Formue.Vurdert.createFromGrunnlag(
        grunnlag = nonEmptyListOf(
            Formuegrunnlag.create(
                periode = periode,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                behandlingsPeriode = periode,
                bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
        ),
    )
}
