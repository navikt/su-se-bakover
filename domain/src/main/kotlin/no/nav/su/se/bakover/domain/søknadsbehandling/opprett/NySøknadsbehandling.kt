package no.nav.su.se.bakover.domain.søknadsbehandling.opprett

import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.StøtterHentingAvEksternGrunnlag
import java.util.UUID

data class NySøknadsbehandling(
    val id: SøknadsbehandlingId,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val søknad: Søknad.Journalført.MedOppgave,
    val oppgaveId: OppgaveId,
    val fnr: Fnr,
    val sakstype: Sakstype,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    init {
        require(sakstype == søknad.type) {
            "Støtter ikke å ha forskjellige typer (uføre, alder) på en og samme sak."
        }
        require(oppgaveId == søknad.oppgaveId) {
            "Søknadsbehandlingens og søknadens oppgaver ($oppgaveId, ${søknad.oppgaveId}) må være like ved opprettelse av søknadsbehandling. For søknadsbehandling: $id og søknad ${søknad.id}"
        }
    }

    val søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
        Søknadsbehandlingshendelse(
            tidspunkt = opprettet,
            saksbehandler = saksbehandler,
            handling = SøknadsbehandlingsHandling.StartetBehandling,
        ),
    )

    fun toSøknadsbehandling(saksnummer: Saksnummer): VilkårsvurdertSøknadsbehandling.Uavklart {
        return VilkårsvurdertSøknadsbehandling.Uavklart(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            søknad = søknad,
            oppgaveId = oppgaveId,
            fnr = fnr,
            fritekstTilBrev = "",
            aldersvurdering = null,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = when (sakstype) {
                    Sakstype.ALDER -> VilkårsvurderingerSøknadsbehandling.Alder.ikkeVurdert()
                    Sakstype.UFØRE -> VilkårsvurderingerSøknadsbehandling.Uføre.ikkeVurdert()
                },
                eksterneGrunnlag = StøtterHentingAvEksternGrunnlag.ikkeHentet(),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
            sakstype = sakstype,
            saksbehandler = saksbehandler,
        )
    }
}
