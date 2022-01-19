package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.grunnlag.FormueVilkårsvurderingRepo
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.domain.grunnlag.UføreVilkårsvurderingRepo
import no.nav.su.se.bakover.domain.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import org.mockito.kotlin.mock

object TestDatabaseBuilder {
    fun build(
        avstemming: AvstemmingRepo = mock(),
        utbetaling: UtbetalingRepo = mock(),
        søknad: SøknadRepo = mock(),
        hendelseslogg: HendelsesloggRepo = mock(),
        sak: SakRepo = mock(),
        person: PersonRepo = mock(),
        søknadsbehandling: SøknadsbehandlingRepo = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        vedtakRepo: VedtakRepo = mock(),
        grunnlagRepo: GrunnlagRepo = mock(),
        uføreVilkårsvurderingRepo: UføreVilkårsvurderingRepo = mock(),
        formueVilkårsvurderingRepo: FormueVilkårsvurderingRepo = mock(),
        personhendelseRepo: PersonhendelseRepo = mock(),
        dokumentRepo: DokumentRepo = mock(),
        nøkkeltallRepo: NøkkeltallRepo = mock(),
        sessionFactory: TestSessionFactory = TestSessionFactory(),
        klageRepo: KlageRepo = mock(),
        klageVedtakRepo: KlagevedtakRepo = mock(),
        kontrollsamtaleRepo: KontrollsamtaleRepo = mock(),
    ): DatabaseRepos {
        return DatabaseRepos(
            avstemming = avstemming,
            utbetaling = utbetaling,
            søknad = søknad,
            hendelseslogg = hendelseslogg,
            sak = sak,
            person = person,
            søknadsbehandling = søknadsbehandling,
            revurderingRepo = revurderingRepo,
            vedtakRepo = vedtakRepo,
            grunnlagRepo = grunnlagRepo,
            uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
            formueVilkårsvurderingRepo = formueVilkårsvurderingRepo,
            personhendelseRepo = personhendelseRepo,
            dokumentRepo = dokumentRepo,
            nøkkeltallRepo = nøkkeltallRepo,
            sessionFactory = sessionFactory,
            klageRepo = klageRepo,
            klageVedtakRepo = klageVedtakRepo,
            kontrollsamtaleRepo = kontrollsamtaleRepo,
        )
    }
}
