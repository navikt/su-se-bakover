package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.jobcontext.JobContextRepo
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import org.mockito.kotlin.mock

object MockDatabaseBuilder {
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
        kontrollsamtaleRepo: KontrollsamtaleRepo = mock(),
        avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
        reguleringRepo: ReguleringRepo = mock(),
        tilbakekrevingRepo: TilbakekrevingRepo = mock(),
        jobContextRepo: JobContextRepo = mock(),
        hendelseRepo: HendelseRepo = mock(),
        utenlandsoppholdRepo: UtenlandsoppholdRepo = mock(),
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
            kontrollsamtaleRepo = kontrollsamtaleRepo,
            avkortingsvarselRepo = avkortingsvarselRepo,
            reguleringRepo = reguleringRepo,
            tilbakekrevingRepo = tilbakekrevingRepo,
            jobContextRepo = jobContextRepo,
            hendelseRepo = hendelseRepo,
            utenlandsoppholdRepo = utenlandsoppholdRepo,
        )
    }
}
