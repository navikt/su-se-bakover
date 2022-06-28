package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import java.util.UUID

object BehandlingTestUtils {
    internal val sakId = UUID.randomUUID()
    internal val saksnummer = Saksnummer(2021)
    internal val søknadId = UUID.randomUUID()
    internal val behandlingId = UUID.randomUUID()
    internal val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    internal val alderssøknadInnhold = søknadsinnholdAlder()
    internal val oppgaveId = OppgaveId("o")
    internal val stønadsperiode = Stønadsperiode.create(år(2021))
}
