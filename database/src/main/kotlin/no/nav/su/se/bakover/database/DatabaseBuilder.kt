package no.nav.su.se.bakover.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.RotatingCredentials
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.StaticCredentials
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.dokument.DokumentPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FamiliegjenforeningVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormuegrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.OpplysningspliktGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.OpplysningspliktVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PensjonVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PensjonsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.klage.klageinstans.KlageinstanshendelsePostgresRepo
import no.nav.su.se.bakover.database.kontrollsamtale.KontrollsamtalePostgresRepo
import no.nav.su.se.bakover.database.nøkkeltall.NøkkeltallPostgresRepo
import no.nav.su.se.bakover.database.person.PersonPostgresRepo
import no.nav.su.se.bakover.database.personhendelse.PersonhendelsePostgresRepo
import no.nav.su.se.bakover.database.regulering.ReguleringPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tilbakekreving.TilbakekrevingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import org.jetbrains.annotations.TestOnly
import java.time.Clock
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(
        databaseConfig: ApplicationConfig.DatabaseConfig,
        dbMetrics: DbMetrics,
        clock: Clock,
        satsFactory: SatsFactoryForSupplerendeStønad,
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
        return buildInternal(userDatastore, dbMetrics, clock, satsFactory)
    }

    @TestOnly
    fun build(
        embeddedDatasource: DataSource,
        dbMetrics: DbMetrics,
        clock: Clock,
        satsFactory: SatsFactoryForSupplerendeStønad,
    ): DatabaseRepos {
        // I testene ønsker vi ikke noe herjing med rolle; embedded-oppsettet sørger for at vi har riktige tilganger og er ferdigmigrert her.
        return buildInternal(embeddedDatasource, dbMetrics, clock, satsFactory)
    }

    @TestOnly
    fun newLocalDataSource(): DataSource {
        val dbConfig = ApplicationConfig.DatabaseConfig.createLocalConfig()
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = dbConfig.jdbcUrl
                this.maximumPoolSize = 3
                this.minimumIdle = 1
                this.idleTimeout = 10001
                this.connectionTimeout = 1000
                this.maxLifetime = 30001
                this.username = dbConfig.username
                this.password = dbConfig.password
            },
        )
    }

    @TestOnly
    fun migrateDatabase(dataSource: DataSource) {
        Flyway(dataSource).migrate()
    }

    internal fun buildInternal(
        dataSource: DataSource,
        dbMetrics: DbMetrics,
        clock: Clock,
        satsFactory: SatsFactoryForSupplerendeStønad,
    ): DatabaseRepos {
        val sessionCounter = SessionCounter()
        val sessionFactory = PostgresSessionFactory(dataSource, dbMetrics, sessionCounter)

        val avkortingsvarselRepo = AvkortingsvarselPostgresRepo(sessionFactory, dbMetrics)

        val grunnlagsdataOgVilkårsvurderingerPostgresRepo = GrunnlagsdataOgVilkårsvurderingerPostgresRepo(
            dbMetrics = dbMetrics,
            bosituasjongrunnlagPostgresRepo = BosituasjongrunnlagPostgresRepo(
                dbMetrics = dbMetrics,
            ),
            fradragsgrunnlagPostgresRepo = FradragsgrunnlagPostgresRepo(
                dbMetrics = dbMetrics,
            ),
            uføreVilkårsvurderingPostgresRepo = UføreVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
                uføregrunnlagRepo = UføregrunnlagPostgresRepo(dbMetrics),
            ),
            formueVilkårsvurderingPostgresRepo = FormueVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
                formuegrunnlagPostgresRepo = FormuegrunnlagPostgresRepo(dbMetrics),
            ),
            utenlandsoppholdVilkårsvurderingPostgresRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
                utenlandsoppholdgrunnlagRepo = UtenlandsoppholdgrunnlagPostgresRepo(dbMetrics),
            ),
            opplysningspliktVilkårsvurderingPostgresRepo = OpplysningspliktVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
                opplysningspliktGrunnlagRepo = OpplysningspliktGrunnlagPostgresRepo(dbMetrics),
            ),
            pensjonVilkårsvurderingPostgresRepo = PensjonVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
                pensjonsgrunnlagPostgresRepo = PensjonsgrunnlagPostgresRepo(dbMetrics),
            ),
            familiegjenforeningVilkårsvurderingPostgresRepo = FamiliegjenforeningVilkårsvurderingPostgresRepo(dbMetrics),
        )

        val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(
            sessionFactory = sessionFactory,
            grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
            dbMetrics = dbMetrics,
            avkortingsvarselRepo = avkortingsvarselRepo,
            clock = clock,
            satsFactory = satsFactory,
        )

        val klageinstanshendelseRepo = KlageinstanshendelsePostgresRepo(sessionFactory, dbMetrics)
        val klageRepo = KlagePostgresRepo(sessionFactory, dbMetrics, klageinstanshendelseRepo)

        val tilbakekrevingRepo = TilbakekrevingPostgresRepo(
            sessionFactory = sessionFactory,
        )
        val reguleringRepo = ReguleringPostgresRepo(
            sessionFactory = sessionFactory,
            grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
            dbMetrics = dbMetrics,
            satsFactory = satsFactory,
        )
        val revurderingRepo = RevurderingPostgresRepo(
            sessionFactory = sessionFactory,
            dbMetrics = dbMetrics,
            grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            klageRepo = klageRepo,
            avkortingsvarselRepo = avkortingsvarselRepo,
            tilbakekrevingRepo = tilbakekrevingRepo,
            reguleringPostgresRepo = reguleringRepo,
            satsFactory = satsFactory,
        )
        val vedtakRepo = VedtakPostgresRepo(
            sessionFactory = sessionFactory,
            dbMetrics = dbMetrics,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            revurderingRepo = revurderingRepo,
            klageRepo = klageRepo,
            reguleringRepo = reguleringRepo,
            satsFactory = satsFactory,
        )
        val hendelseRepo = PersonhendelsePostgresRepo(sessionFactory, dbMetrics, clock)
        val nøkkeltallRepo = NøkkeltallPostgresRepo(sessionFactory, dbMetrics, clock)
        val kontrollsamtaleRepo = KontrollsamtalePostgresRepo(sessionFactory, dbMetrics)

        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(sessionFactory, dbMetrics),
            utbetaling = UtbetalingPostgresRepo(
                sessionFactory = sessionFactory,
                dbMetrics = dbMetrics,
            ),
            søknad = SøknadPostgresRepo(
                sessionFactory = sessionFactory,
                dbMetrics = dbMetrics,
            ),
            sak = SakPostgresRepo(
                sessionFactory = sessionFactory,
                dbMetrics = dbMetrics,
                søknadsbehandlingRepo = søknadsbehandlingRepo,
                revurderingRepo = revurderingRepo,
                vedtakPostgresRepo = vedtakRepo,
                klageRepo = klageRepo,
                reguleringRepo = reguleringRepo,
            ),
            person = PersonPostgresRepo(
                sessionFactory = sessionFactory,
                dbMetrics = dbMetrics,
            ),
            søknadsbehandling = søknadsbehandlingRepo,
            revurderingRepo = revurderingRepo,
            vedtakRepo = vedtakRepo,
            personhendelseRepo = hendelseRepo,
            dokumentRepo = DokumentPostgresRepo(sessionFactory, dbMetrics),
            nøkkeltallRepo = nøkkeltallRepo,
            sessionFactory = sessionFactory,
            klageRepo = klageRepo,
            klageinstanshendelseRepo = klageinstanshendelseRepo,
            kontrollsamtaleRepo = kontrollsamtaleRepo,
            avkortingsvarselRepo = avkortingsvarselRepo,
            reguleringRepo = reguleringRepo,
            tilbakekrevingRepo = tilbakekrevingRepo,
            jobContextRepo = JobContextPostgresRepo(sessionFactory),
        )
    }
}
