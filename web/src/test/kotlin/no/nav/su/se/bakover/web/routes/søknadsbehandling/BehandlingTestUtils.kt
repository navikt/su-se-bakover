package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.generer
import java.util.UUID

object BehandlingTestUtils {
    internal val sakId = UUID.randomUUID()
    internal val saksnummer = Saksnummer(2021)
    internal val søknadId = UUID.randomUUID()
    internal val behandlingId = UUID.randomUUID()
    internal val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    internal val alderssøknadInnhold = søknadsinnholdAlder()
    internal val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    internal val stønadsperiode =
        Stønadsperiode.create(år(2021))
    internal val journalførtSøknadMedOppgave = Søknad.Journalført.MedOppgave.IkkeLukket(
        sakId = sakId,
        opprettet = Tidspunkt.EPOCH,
        id = søknadId,
        søknadInnhold = søknadInnhold,
        oppgaveId = oppgaveId,
        journalpostId = journalpostId,
    )
    internal val fnr = Fnr.generer()

    //     Søknadsbehandling.Iverksatt.Innvilget(
    //     id = behandlingId,
    //     opprettet = Tidspunkt.EPOCH,
    //     sakId = sakId,
    //     saksnummer = saksnummer,
    //     søknad = journalførtSøknadMedOppgave,
    //     oppgaveId = oppgaveId,
    //     behandlingsinformasjon = Behandlingsinformasjon(
    //         flyktning = Behandlingsinformasjon.Flyktning(
    //             status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
    //         ),
    //         fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
    //             status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
    //         ),
    //         institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
    //             status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
    //         ),
    //         personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
    //             status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
    //         ),
    //     ),
    //     fnr = fnr,
    //     beregning = TestBeregning,
    //     simulering = Simulering(
    //         gjelderId = fnr,
    //         gjelderNavn = "navn",
    //         datoBeregnet = LocalDate.EPOCH,
    //         nettoBeløp = 1000,
    //         periodeList = listOf(),
    //     ),
    //     saksbehandler = NavIdentBruker.Saksbehandler("pro-saksbehandler"),
    //     attesteringer = Attesteringshistorikk.empty()
    //         .leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant("kjella"), Tidspunkt.EPOCH)),
    //     fritekstTilBrev = "",
    //     stønadsperiode = stønadsperiode,
    //     grunnlagsdata = Grunnlagsdata.create(
    //         bosituasjon = listOf(bosituasjon),
    //     ),
    //     vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.Uføre(
    //         uføre = Vilkår.Uførhet.Vurdert.create(
    //             vurderingsperioder = nonEmptyListOf(vurderingsperiodeUføre),
    //         ),
    //         utenlandsopphold = utenlandsopphold,
    //         formue = formuevilkårIkkeVurdert(),
    //         opplysningsplikt = tilstrekkeligDokumentert(
    //             periode = stønadsperiode.periode
    //         ),
    //         lovligOpphold = lovligOppholdVilkårInnvilget(),
    //         // TODO jah: Ikke bra at dette ender opp som et innvilget vedtak uten å gå via oppdater(behandlingsinformasjon)-funksjonen.
    //         fastOpphold = fastOppholdVilkårInnvilget(),
    //         institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
    //         personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
    //         flyktning = flykt,
    //     ),
    //     avkorting = AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående,
    //     sakstype = Sakstype.UFØRE,
    // )
}
