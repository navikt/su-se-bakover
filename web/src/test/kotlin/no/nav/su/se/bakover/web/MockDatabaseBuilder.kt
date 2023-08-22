package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseJobbRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import org.mockito.kotlin.mock

data object MockDatabaseBuilder {
    fun build(
        avstemming: AvstemmingRepo = mock(),
        utbetaling: UtbetalingRepo = mock(),
        søknad: SøknadRepo = mock(),
        sak: SakRepo = mock(),
        person: PersonRepo = mock(),
        søknadsbehandling: SøknadsbehandlingRepo = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        vedtakRepo: VedtakRepo = mock(),
        personhendelseRepo: PersonhendelseRepo = mock(),
        dokumentRepo: DokumentRepo = mock(),
        nøkkeltallRepo: NøkkeltallRepo = mock(),
        sessionFactory: PostgresSessionFactory = mock(),
        klageRepo: KlageRepo = mock(),
        klageinstanshendelseRepo: KlageinstanshendelseRepo = mock(),
        avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
        reguleringRepo: ReguleringRepo = mock(),
        tilbakekrevingRepo: TilbakekrevingRepo = mock(),
        hendelseRepo: HendelseRepo = mock(),
        utenlandsoppholdRepo: UtenlandsoppholdRepo = mock(),
        sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo = mock(),
        dokumentSkattRepo: DokumentSkattRepo = mock(),
        institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo = mock(),
        oppgaveHendelseRepo: OppgaveHendelseRepo = mock(),
        hendelseJobbRepo: HendelseJobbRepo = mock(),
    ): DatabaseRepos {
        return DatabaseRepos(
            avstemming = avstemming,
            utbetaling = utbetaling,
            søknad = søknad,
            sak = sak,
            person = person,
            søknadsbehandling = søknadsbehandling,
            revurderingRepo = revurderingRepo,
            vedtakRepo = vedtakRepo,
            personhendelseRepo = personhendelseRepo,
            dokumentRepo = dokumentRepo,
            nøkkeltallRepo = nøkkeltallRepo,
            sessionFactory = sessionFactory,
            klageRepo = klageRepo,
            klageinstanshendelseRepo = klageinstanshendelseRepo,
            avkortingsvarselRepo = avkortingsvarselRepo,
            reguleringRepo = reguleringRepo,
            tilbakekrevingRepo = tilbakekrevingRepo,
            sendPåminnelseNyStønadsperiodeJobRepo = sendPåminnelseNyStønadsperiodeJobRepo,
            hendelseRepo = hendelseRepo,
            utenlandsoppholdRepo = utenlandsoppholdRepo,
            dokumentSkattRepo = dokumentSkattRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            hendelseJobbRepo = hendelseJobbRepo,
        )
    }
}
