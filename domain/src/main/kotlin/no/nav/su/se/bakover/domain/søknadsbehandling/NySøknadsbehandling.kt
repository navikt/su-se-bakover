package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

data class NySøknadsbehandling(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val søknad: Søknad.Journalført.MedOppgave,
    val oppgaveId: OppgaveId,
    val fnr: Fnr,
    val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
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

    fun toSøknadsbehandling(saksnummer: Saksnummer): Søknadsbehandling.Vilkårsvurdert.Uavklart {
        return Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            søknad = søknad,
            oppgaveId = oppgaveId,
            fnr = fnr,
            fritekstTilBrev = "",
            stønadsperiode = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = when (sakstype) {
                Sakstype.ALDER -> Vilkårsvurderinger.Søknadsbehandling.Alder.ikkeVurdert()
                Sakstype.UFØRE -> Vilkårsvurderinger.Søknadsbehandling.Uføre.ikkeVurdert()
            },
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = avkorting,
            sakstype = sakstype,
            saksbehandler = saksbehandler,
        )
    }
}
