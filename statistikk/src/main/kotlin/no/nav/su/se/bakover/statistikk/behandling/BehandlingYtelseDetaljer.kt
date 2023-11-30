package no.nav.su.se.bakover.statistikk.behandling

import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto.Companion.stønadsklassifisering

internal fun Stønadsbehandling.behandlingYtelseDetaljer(): List<BehandlingsstatistikkDto.BehandlingYtelseDetaljer> {
    return this.grunnlagsdata.bosituasjon.filterIsInstance<Bosituasjon.Fullstendig>().map {
        BehandlingsstatistikkDto.BehandlingYtelseDetaljer(satsgrunn = it.stønadsklassifisering())
    }
}
