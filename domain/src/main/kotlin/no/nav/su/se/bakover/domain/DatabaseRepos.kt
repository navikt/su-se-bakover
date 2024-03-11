package no.nav.su.se.bakover.domain

import dokument.domain.DokumentRepo
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingUnderRevurderingRepo
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
import person.domain.PersonRepo
import vilkår.skatt.domain.DokumentSkattRepo
import vilkår.utenlandsopphold.domain.UtenlandsoppholdRepo
import økonomi.domain.utbetaling.UtbetalingRepo

data class DatabaseRepos(
    val avstemming: AvstemmingRepo,
    val utbetaling: UtbetalingRepo,
    val søknad: SøknadRepo,
    val sak: SakRepo,
    val person: PersonRepo,
    val søknadsbehandling: SøknadsbehandlingRepo,
    val revurderingRepo: RevurderingRepo,
    val vedtakRepo: VedtakRepo,
    val personhendelseRepo: PersonhendelseRepo,
    val dokumentRepo: DokumentRepo,
    val nøkkeltallRepo: NøkkeltallRepo,
    val sessionFactory: SessionFactory,
    val klageRepo: KlageRepo,
    val klageinstanshendelseRepo: KlageinstanshendelseRepo,
    val reguleringRepo: ReguleringRepo,
    val tilbakekrevingRepo: TilbakekrevingUnderRevurderingRepo,
    val sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo,
    val hendelseRepo: HendelseRepo,
    val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    val dokumentSkattRepo: DokumentSkattRepo,
    val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo,
    val oppgaveHendelseRepo: OppgaveHendelseRepo,
    val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    val dokumentHendelseRepo: DokumentHendelseRepo,
)
