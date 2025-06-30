package no.nav.su.se.bakover.statistikk.behandling

import behandling.domain.Stønadsbehandling
import statistikk.domain.StønadsklassifiseringDto.Companion.stønadsklassifisering
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon

internal fun Stønadsbehandling.behandlingYtelseDetaljer(): List<BehandlingsstatistikkDto.BehandlingYtelseDetaljer> {
    return this.grunnlagsdata.bosituasjon.filterIsInstance<Bosituasjon.Fullstendig>().map {
        BehandlingsstatistikkDto.BehandlingYtelseDetaljer(satsgrunn = it.stønadsklassifisering())
    }
}
