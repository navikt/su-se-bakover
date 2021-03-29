package no.nav.su.se.bakover.web.routes.revurdering

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.sak.sakPath
import java.util.UUID

object RevurderingRoutesTestData {

    internal val sakId = UUID.randomUUID()
    internal val requestPath = "$sakPath/$sakId/revurderinger"
    internal val testServices = TestServicesBuilder.services()
    internal val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))

    internal val vedtak = Vedtak.EndringIYtelse.fromSøknadsbehandling(
        Søknadsbehandling.Iverksatt.Innvilget(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            saksnummer = Saksnummer(1569),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                journalpostId = JournalpostId(value = ""),
                oppgaveId = OppgaveId(value = "")

            ),
            oppgaveId = OppgaveId(value = ""),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
                bosituasjon = Behandlingsinformasjon.Bosituasjon(
                    ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = true,
                    ektemakeEllerSamboerUførFlyktning = true,
                    begrunnelse = null
                ),
                ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
            ),
            fnr = FnrGenerator.random(),
            beregning = TestBeregning,
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saks"),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")),
            fritekstTilBrev = "",
        ),
        UUID30.randomUUID()
    )
}
