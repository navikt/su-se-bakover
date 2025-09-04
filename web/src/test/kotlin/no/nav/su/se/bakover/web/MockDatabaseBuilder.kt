package no.nav.su.se.bakover.web

import dokument.domain.DokumentRepo
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import nøkkeltall.domain.NøkkeltallRepo
import org.mockito.kotlin.mock
import person.domain.PersonRepo
import vilkår.skatt.domain.DokumentSkattRepo
import vilkår.utenlandsopphold.domain.UtenlandsoppholdRepo
import økonomi.domain.utbetaling.UtbetalingRepo
// Disse kan aldri overrides så hvorfor late som?
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
        reguleringRepo: ReguleringRepo = mock(),
        hendelseRepo: HendelseRepo = mock(),
        utenlandsoppholdRepo: UtenlandsoppholdRepo = mock(),
        sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo = mock(),
        dokumentSkattRepo: DokumentSkattRepo = mock(),
        institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo = mock(),
        oppgaveHendelseRepo: OppgaveHendelseRepo = mock(),
        hendelsekonsumenterRepo: HendelsekonsumenterRepo = mock(),
        dokumentHendelseRepo: DokumentHendelseRepo = mock(),
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
            reguleringRepo = reguleringRepo,
            sendPåminnelseNyStønadsperiodeJobRepo = sendPåminnelseNyStønadsperiodeJobRepo,
            hendelseRepo = hendelseRepo,
            utenlandsoppholdRepo = utenlandsoppholdRepo,
            dokumentSkattRepo = dokumentSkattRepo,
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            dokumentHendelseRepo = dokumentHendelseRepo,
            stønadStatistikkRepo = mock(),
            sakStatistikkRepo = mock(),
        )
    }
}
