package no.nav.su.se.bakover.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.RotatingCredentials
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.StaticCredentials
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.Flyway
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.QueryParameterMapper
import no.nav.su.se.bakover.common.persistence.SessionCounter
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.dokument.DokumentPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.EksternGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FamiliegjenforeningVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FastOppholdINorgeVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FlyktningVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormuegrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.InstitusjonsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.OpplysningspliktGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.OpplysningspliktVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PensjonVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PensjonsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PersonligOppmøteGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PersonligOppmøteVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.klage.klageinstans.KlageinstanshendelsePostgresRepo
import no.nav.su.se.bakover.database.nøkkeltall.NøkkeltallPostgresRepo
import no.nav.su.se.bakover.database.person.PersonPostgresRepo
import no.nav.su.se.bakover.database.personhendelse.PersonhendelsePostgresRepo
import no.nav.su.se.bakover.database.regulering.ReguleringPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.skatt.SkattPostgresRepo
import no.nav.su.se.bakover.database.stønadsperiode.SendPåminnelseNyStønadsperiodeJobPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tilbakekreving.TilbakekrevingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.UtenlandsoppholdPostgresRepo
import org.jetbrains.annotations.TestOnly
import java.time.Clock
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(
        databaseConfig: ApplicationConfig.DatabaseConfig,
        dbMetrics: DbMetrics,
        clock: Clock,
        satsFactory: SatsFactoryForSupplerendeStønad,
        queryParameterMappers: List<QueryParameterMapper>,
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
        return buildInternal(userDatastore, dbMetrics, clock, satsFactory, queryParameterMappers)
    }

    @TestOnly
    fun build(
        embeddedDatasource: DataSource,
        dbMetrics: DbMetrics,
        clock: Clock,
        satsFactory: SatsFactoryForSupplerendeStønad,
        queryParameterMappers: List<QueryParameterMapper> = listOf(DomainToQueryParameterMapper),
    ): DatabaseRepos {
        // I testene ønsker vi ikke noe herjing med rolle; embedded-oppsettet sørger for at vi har riktige tilganger og er ferdigmigrert her.
        return buildInternal(embeddedDatasource, dbMetrics, clock, satsFactory, queryParameterMappers)
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
        queryParameterMappers: List<QueryParameterMapper> = listOf(DomainToQueryParameterMapper),
    ): DatabaseRepos {
        val sessionCounter = SessionCounter()
        val sessionFactory = PostgresSessionFactory(
            dataSource = dataSource,
            dbMetrics = dbMetrics,
            sessionCounter = sessionCounter,
            queryParameterMappers = queryParameterMappers,
        )

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
            lovligOppholdVilkårsvurderingPostgresRepo = LovligOppholdVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
                lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(dbMetrics),
            ),
            flyktningVilkårsvurderingPostgresRepo = FlyktningVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
            ),
            fastOppholdINorgeVilkårsvurderingPostgresRepo = FastOppholdINorgeVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetrics,
            ),
            personligOppmøteVilkårsvurderingPostgresRepo = PersonligOppmøteVilkårsvurderingPostgresRepo(
                personligOppmøteGrunnlagPostgresRepo = PersonligOppmøteGrunnlagPostgresRepo(
                    dbMetrics = dbMetrics,
                ),
                dbMetrics = dbMetrics,
            ),
            institusjonsoppholdVilkårsvurderingPostgresRepo = InstitusjonsoppholdVilkårsvurderingPostgresRepo(dbMetrics),
        )

        val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(
            sessionFactory = sessionFactory,
            grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
            dbMetrics = dbMetrics,
            avkortingsvarselRepo = avkortingsvarselRepo,
            eksterneGrunnlag = EksternGrunnlagPostgresRepo(skattRepo = SkattPostgresRepo),
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
            avkortingsvarselRepo = avkortingsvarselRepo,
            tilbakekrevingRepo = tilbakekrevingRepo,
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
        val personhendelseRepo = PersonhendelsePostgresRepo(sessionFactory, dbMetrics, clock)
        val nøkkeltallRepo = NøkkeltallPostgresRepo(sessionFactory, dbMetrics, clock)

        val hendelseRepo = HendelsePostgresRepo(
            sessionFactory,
            dbMetrics = dbMetrics,
        )
        val utenlandsoppholdRepo = UtenlandsoppholdPostgresRepo(
            hendelseRepo = hendelseRepo,
            sessionFactory = sessionFactory,
            dbMetrics = dbMetrics,
            clock = clock,
        )
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
                avkortingsvarselRepo = avkortingsvarselRepo,
                utenlandsoppholdRepo = utenlandsoppholdRepo,
                hendelseRepo = hendelseRepo,
            ),
            person = PersonPostgresRepo(
                sessionFactory = sessionFactory,
                dbMetrics = dbMetrics,
            ),
            søknadsbehandling = søknadsbehandlingRepo,
            revurderingRepo = revurderingRepo,
            vedtakRepo = vedtakRepo,
            personhendelseRepo = personhendelseRepo,
            dokumentRepo = DokumentPostgresRepo(sessionFactory, dbMetrics, clock),
            nøkkeltallRepo = nøkkeltallRepo,
            sessionFactory = sessionFactory,
            klageRepo = klageRepo,
            klageinstanshendelseRepo = klageinstanshendelseRepo,
            avkortingsvarselRepo = avkortingsvarselRepo,
            reguleringRepo = reguleringRepo,
            tilbakekrevingRepo = tilbakekrevingRepo,
            sendPåminnelseNyStønadsperiodeJobRepo = SendPåminnelseNyStønadsperiodeJobPostgresRepo(
                JobContextPostgresRepo(
                    sessionFactory,
                ),
            ),
            hendelseRepo = hendelseRepo,
            utenlandsoppholdRepo = utenlandsoppholdRepo,
        )
    }
}
