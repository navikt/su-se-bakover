package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Vilkårsvurdering

interface ObjectRepo {
    fun hentSak(fnr: Fnr): Sak?
    fun hentSak(sakId: Long): Sak?
    fun opprettSak(fnr: Fnr): Sak
    fun hentStønadsperioder(sakId: Long): MutableList<Stønadsperiode>
    fun hentSøknad(søknadId: Long): Søknad?
    fun hentBehandling(behandlingId: Long): Behandling?
    fun hentBehandlinger(stønadsperiodeId: Long): MutableList<Behandling>
    fun hentStønadsperiode(stønadsperiodeId: Long): Stønadsperiode?
    fun hentVilkårsvurderinger(behandlingId: Long): MutableList<Vilkårsvurdering>
    fun hentVilkårsvurdering(vilkårsvurderingId: Long): Vilkårsvurdering?
}
