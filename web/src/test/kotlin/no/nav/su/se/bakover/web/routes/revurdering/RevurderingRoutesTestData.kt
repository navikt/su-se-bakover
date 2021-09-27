package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
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
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.stønadsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.TestBeregning
import org.mockito.kotlin.mock
import java.util.UUID

object RevurderingRoutesTestData {

    internal val sakId = UUID.randomUUID()
    internal val requestPath = "$sakPath/$sakId/revurderinger"
    internal val testServices = TestServicesBuilder.services()
    internal val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))

    internal val vedtak = Vedtak.fromSøknadsbehandling(
        søknadsbehandling = Søknadsbehandling.Iverksatt.Innvilget(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                journalpostId = JournalpostId(value = ""),
                oppgaveId = OppgaveId(value = ""),

            ),
            oppgaveId = OppgaveId(value = ""),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
                bosituasjon = Behandlingsinformasjon.Bosituasjon(
                    ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = true,
                    ektemakeEllerSamboerUførFlyktning = true,
                    begrunnelse = null,
                ),
                ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
            ),
            fnr = Fnr.generer(),
            beregning = TestBeregning,
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saks"),
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"), Tidspunkt.now())),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        ),
        utbetalingId = UUID30.randomUUID(),
        clock = fixedClock,
    )

    internal val opprettetRevurdering = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = periode,
        opprettet = Tidspunkt.now(),
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
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        attesteringer = Attesteringshistorikk.empty(),
    )

    internal fun formueVilkår(periode: Periode) = Vilkår.Formue.Vurdert.createFromGrunnlag(
        grunnlag = nonEmptyListOf(
            Formuegrunnlag.create(
                periode = periode,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                behandlingsPeriode = periode,
                bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
        ),
    )
}
