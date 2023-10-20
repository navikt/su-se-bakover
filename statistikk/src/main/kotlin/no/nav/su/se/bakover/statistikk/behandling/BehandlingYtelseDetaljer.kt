package no.nav.su.se.bakover.statistikk.behandling

import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto.Companion.stønadsklassifisering

internal fun Stønadsbehandling.behandlingYtelseDetaljer(): List<BehandlingsstatistikkDto.BehandlingYtelseDetaljer> {
    return this.grunnlagsdata.bosituasjon.filterIsInstance<Grunnlag.Bosituasjon.Fullstendig>().map {
        BehandlingsstatistikkDto.BehandlingYtelseDetaljer(satsgrunn = it.stønadsklassifisering())
    }
}
