package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.persistence.SessionFactory
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
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo

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
    val avkortingsvarselRepo: AvkortingsvarselRepo,
    val reguleringRepo: ReguleringRepo,
    val tilbakekrevingRepo: TilbakekrevingRepo,
    val sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo,
    val hendelseRepo: HendelseRepo,
    val utenlandsoppholdRepo: UtenlandsoppholdRepo,
)
