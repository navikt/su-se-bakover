package no.nav.su.se.bakover.service.behandling

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.FastOppholdINorge
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.test.generer
import java.util.UUID

object BehandlingTestUtils {

    internal val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    internal val saksnummer = Saksnummer(999999)
    internal val søknadId: UUID = UUID.fromString("8707a819-71de-43e4-9b6e-c9a912bb1f70")
    internal val behandlingId: UUID = UUID.fromString("a602aa68-c989-43e3-9fb7-cb488a2a3821")
    internal val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlinger")
    internal val attestant = NavIdentBruker.Attestant("attestant")
    internal val søknadJournalpostId = JournalpostId("søknadJournalpostId")
    internal val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    internal val fnr = Fnr.generer()
    internal val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )

    internal val behandlingsinformasjon = Behandlingsinformasjon(
        fastOppholdINorge = FastOppholdINorge(
            status = FastOppholdINorge.Status.VilkårOppfylt,
        ),
        institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
        ),
    )
}
