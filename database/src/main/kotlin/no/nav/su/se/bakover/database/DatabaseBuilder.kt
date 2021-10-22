package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.RotatingCredentials
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.StaticCredentials
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.database.dokument.DokumentPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingRepo
import no.nav.su.se.bakover.database.grunnlag.FormuegrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.hendelse.PersonhendelsePostgresRepo
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.nøkkeltall.NøkkeltallPostgresRepo
import no.nav.su.se.bakover.database.person.PersonPostgresRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotPostgresRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotRepo
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo
import org.jetbrains.annotations.TestOnly
import java.time.Clock
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(
        databaseConfig: ApplicationConfig.DatabaseConfig,
        dbMetrics: DbMetrics,
        clock: Clock,
    ): DatabaseRepos {
        val abstractDatasource = Postgres(databaseConfig = databaseConfig).build()

        val dataSource = abstractDatasource.getDatasource(Postgres.Role.Admin)
        when (databaseConfig) {
            is StaticCredentials -> {
                // Lokalt ønsker vi ikke noe herjing med rolle; Docker-oppsettet sørger for at vi har riktige tilganger her.
                Flyway(dataSource)
            }
            is RotatingCredentials -> Flyway(
                dataSource = dataSource,
                // Pga roterende credentials i preprod/prod må tabeller opprettes/endres av samme rolle hver gang. Se https://github.com/navikt/utvikling/blob/master/PostgreSQL.md#hvordan-kj%C3%B8re-flyway-migreringerendre-p%C3%A5-databaseskjemaet
                role = "${databaseConfig.databaseName}-${Postgres.Role.Admin}",
            )
        }.migrate()

        val userDatastore = abstractDatasource.getDatasource(Postgres.Role.User)
        return buildInternal(userDatastore, dbMetrics, clock)
    }

    @TestOnly
    fun build(
        embeddedDatasource: DataSource,
        dbMetrics: DbMetrics,
        clock: Clock,
    ): DatabaseRepos {
        // I testene ønsker vi ikke noe herjing med rolle; embedded-oppsettet sørger for at vi har riktige tilganger og er ferdigmigrert her.
        return buildInternal(embeddedDatasource, dbMetrics, clock)
    }

    internal fun buildInternal(
        dataSource: DataSource,
        dbMetrics: DbMetrics,
        clock: Clock,
    ): DatabaseRepos {
        val sessionFactory = PostgresSessionFactory(dataSource)

        val uføregrunnlagRepo = UføregrunnlagPostgresRepo()
        val fradragsgrunnlag = FradragsgrunnlagPostgresRepo(
            dataSource = dataSource,
            dbMetrics = dbMetrics,
        )
        val bosituasjongrunnlag = BosituasjongrunnlagPostgresRepo(
            dataSource = dataSource,
            dbMetrics = dbMetrics,
        )
        val formuegrunnlagRepo = FormuegrunnlagPostgresRepo()

        val grunnlagRepo = GrunnlagPostgresRepo(
            fradragsgrunnlagRepo = fradragsgrunnlag,
            bosituasjongrunnlagRepo = bosituasjongrunnlag,
        )

        val uføreVilkårsvurderingRepo = UføreVilkårsvurderingPostgresRepo(
            dataSource = dataSource,
            uføregrunnlagRepo = uføregrunnlagRepo,
            dbMetrics = dbMetrics,
        )

        val formueVilkårsvurderingRepo = FormueVilkårsvurderingPostgresRepo(
            dataSource = dataSource,
            formuegrunnlagPostgresRepo = formuegrunnlagRepo,
            dbMetrics = dbMetrics,
        )

        val saksbehandlingRepo = SøknadsbehandlingPostgresRepo(
            dataSource = dataSource,
            fradragsgrunnlagPostgresRepo = fradragsgrunnlag,
            bosituasjongrunnlagRepo = bosituasjongrunnlag,
            uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
            dbMetrics = dbMetrics,
            sessionFactory = sessionFactory
        )

        val revurderingRepo = RevurderingPostgresRepo(
            dataSource = dataSource,
            fradragsgrunnlagPostgresRepo = fradragsgrunnlag,
            bosituasjonsgrunnlagPostgresRepo = bosituasjongrunnlag,
            uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
            formueVilkårsvurderingRepo = formueVilkårsvurderingRepo,
            søknadsbehandlingRepo = saksbehandlingRepo,
            dbMetrics = dbMetrics,
            sessionFactory = sessionFactory,
        )
        val vedtakRepo = VedtakPostgresRepo(
            dataSource = dataSource,
            søknadsbehandlingRepo = saksbehandlingRepo,
            revurderingRepo = revurderingRepo,
            dbMetrics = dbMetrics,
            sessionFactory = sessionFactory,
        )
        val hendelseRepo = PersonhendelsePostgresRepo(dataSource, clock)
        val nøkkeltallRepo = NøkkeltallPostgresRepo(dataSource)

        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(dataSource),
            utbetaling = UtbetalingPostgresRepo(
                dataSource = dataSource,
                dbMetrics = dbMetrics,
            ),
            søknad = SøknadPostgresRepo(
                dataSource = dataSource,
                dbMetrics = dbMetrics,
                postgresSessionFactory = sessionFactory,
            ),
            hendelseslogg = HendelsesloggPostgresRepo(dataSource),
            sak = SakPostgresRepo(
                sessionFactory = sessionFactory,
                søknadsbehandlingRepo = saksbehandlingRepo,
                revurderingRepo = revurderingRepo,
                vedtakPostgresRepo = vedtakRepo,
                dbMetrics = dbMetrics,
            ),
            person = PersonPostgresRepo(
                dataSource = dataSource,
                dbMetrics = dbMetrics,
            ),
            vedtakssnapshot = VedtakssnapshotPostgresRepo(dataSource),
            søknadsbehandling = saksbehandlingRepo,
            revurderingRepo = revurderingRepo,
            vedtakRepo = vedtakRepo,
            grunnlagRepo = grunnlagRepo,
            uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
            formueVilkårsvurderingRepo = formueVilkårsvurderingRepo,
            dokumentRepo = DokumentPostgresRepo(dataSource, sessionFactory),
            personhendelseRepo = hendelseRepo,
            nøkkeltallRepo = nøkkeltallRepo,
            sessionFactory = sessionFactory,
        )
    }
}

data class DatabaseRepos(
    val avstemming: AvstemmingRepo,
    val utbetaling: UtbetalingRepo,
    val søknad: SøknadRepo,
    val hendelseslogg: HendelsesloggRepo,
    val sak: SakRepo,
    val person: PersonRepo,
    val vedtakssnapshot: VedtakssnapshotRepo,
    val søknadsbehandling: SøknadsbehandlingRepo,
    val revurderingRepo: RevurderingRepo,
    val vedtakRepo: VedtakRepo,
    val grunnlagRepo: GrunnlagRepo,
    val uføreVilkårsvurderingRepo: UføreVilkårsvurderingRepo,
    val formueVilkårsvurderingRepo: FormueVilkårsvurderingRepo,
    val personhendelseRepo: PersonhendelseRepo,
    val dokumentRepo: DokumentRepo,
    val nøkkeltallRepo: NøkkeltallRepo,
    val sessionFactory: SessionFactory,
)
