package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.*

interface ObjectRepo {
    fun hentSak(fnr: Fnr): Sak?
    fun hentSak(sakId: Long): Sak?
    fun opprettSak(fnr: Fnr): Sak
    fun hentStønadsperioder(sakId: Long): MutableList<Stønadsperiode>
    fun hentSøknad(søknadId: Long): Søknad?
    fun hentBehandling(behandlingId: Long): Behandling?
    fun hentStønadsperiode(stønadsperiodeId: Long): Stønadsperiode?
    fun hentVilkårsvurderinger(behandlingId: Long): MutableList<Vilkårsvurdering>
    fun hentVilkårsvurdering(vilkårsvurderingId: Long): Vilkårsvurdering
}
