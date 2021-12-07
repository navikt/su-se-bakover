package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.database.AvkortingsvarselRepo
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingRepo
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo
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
        avkortingsvarselRepo: AvkortingsvarselRepo = mock(),

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
            avkortingsvarselRepo = avkortingsvarselRepo,
        )
    }
}
