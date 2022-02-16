package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.bruker.NavIdentBruker
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.Fnr
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.routes.grunnlag.BosituasjonJsonTest.Companion.bosituasjon
import no.nav.su.se.bakover.web.routes.grunnlag.UføreVilkårJsonTest.Companion.vurderingsperiodeUføre
import no.nav.su.se.bakover.web.routes.grunnlag.UtenlandsoppholdVilkårJsonTest.Companion.utenlandsopphold
import java.time.LocalDate
import java.util.UUID

object BehandlingTestUtils {
    internal val sakId = UUID.randomUUID()
    internal val saksnummer = Saksnummer(2021)
    internal val søknadId = UUID.randomUUID()
    internal val behandlingId = UUID.randomUUID()
    internal val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    internal val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    internal val stønadsperiode =
        Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)), "begrunnelsen")
    internal val journalførtSøknadMedOppgave = Søknad.Journalført.MedOppgave.IkkeLukket(
        sakId = sakId,
        opprettet = Tidspunkt.EPOCH,
        id = søknadId,
        søknadInnhold = søknadInnhold,
        oppgaveId = oppgaveId,
        journalpostId = journalpostId,
    )
    internal val fnr = Fnr.generer()

    internal fun innvilgetSøknadsbehandling() = Søknadsbehandling.Iverksatt.Innvilget(
        id = behandlingId,
        opprettet = Tidspunkt.EPOCH,
        sakId = sakId,
        saksnummer = saksnummer,
        søknad = journalførtSøknadMedOppgave,
        oppgaveId = oppgaveId,
        behandlingsinformasjon = Behandlingsinformasjon(
            flyktning = Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
                begrunnelse = null,
            ),
            lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
                begrunnelse = null,
            ),
            fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
                begrunnelse = null,
            ),
            institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
                status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
                begrunnelse = null,
            ),
            formue = Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                verdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiEiendommer = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0,
                ),
                epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiEiendommer = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0,
                ),
                begrunnelse = null,
            ),
            personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
                begrunnelse = null,
            ),
        ),
        fnr = fnr,
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 1000,
            periodeList = listOf(),
        ),
        saksbehandler = NavIdentBruker.Saksbehandler("pro-saksbehandler"),
        attesteringer = Attesteringshistorikk.empty()
            .leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant("kjella"), Tidspunkt.EPOCH)),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.create(
            bosituasjon = listOf(bosituasjon),
        ),
        vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(vurderingsperiodeUføre),
            ),
            utenlandsopphold = utenlandsopphold,
        ),
        avkorting = AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående,
    )
}
