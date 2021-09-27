package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

fun NySøknadsbehandling.toSøknadsbehandling(saksnummer: Saksnummer): Søknadsbehandling.Vilkårsvurdert.Uavklart {
    return Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = this.id,
        opprettet = this.opprettet,
        sakId = this.sakId,
        saksnummer = saksnummer,
        søknad = this.søknad,
        oppgaveId = this.søknad.oppgaveId,
        behandlingsinformasjon = this.behandlingsinformasjon,
        fnr = this.fnr,
        fritekstTilBrev = "",
        stønadsperiode = null,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        attesteringer = Attesteringshistorikk.empty(),
    )
}
