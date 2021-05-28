package no.nav.su.se.bakover.service.vilkår

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.time.Clock
import java.util.UUID

data class LeggTilBosituasjonEpsRequest(
    val behandlingId: UUID,
    val epsFnr: Fnr?,
) {
    fun toBosituasjon(periode: Periode, clock: Clock): Grunnlag.Bosituasjon.Ufullstendig {
        return if (epsFnr == null ) {
            Grunnlag.Bosituasjon.Ufullstendig.HarValgtEPSIkkeValgtEnsligVoksne(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = periode,
            )
        } else {
            Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = periode,
                fnr = epsFnr,
            )
        }
    }
}
