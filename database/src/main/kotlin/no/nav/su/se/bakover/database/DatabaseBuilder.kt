package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.RotatingCredentials
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.StaticCredentials
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
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
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
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
import no.nav.su.se.bakover.database.vedtak.VedtakPosgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotPostgresRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotRepo
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(databaseConfig: ApplicationConfig.DatabaseConfig): DatabaseRepos {
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
        return buildInternal(userDatastore)
    }

    fun build(embeddedDatasource: DataSource): DatabaseRepos {
        // I testene ønsker vi ikke noe herjing med rolle; embedded-oppsettet sørger for at vi har riktige tilganger her.
        Flyway(embeddedDatasource).migrate()
        return buildInternal(embeddedDatasource)
    }

    private fun buildInternal(dataSource: DataSource): DatabaseRepos {
        val uføregrunnlagRepo = UføregrunnlagPostgresRepo()
        val fradragsgrunnlag = FradragsgrunnlagPostgresRepo(dataSource)
        val bosituasjongrunnlag = BosituasjongrunnlagPostgresRepo(dataSource)
        val formuegrunnlagRepo = FormuegrunnlagPostgresRepo()

        val grunnlagRepo = GrunnlagPostgresRepo(
            fradragsgrunnlagRepo = fradragsgrunnlag,
            bosituasjongrunnlagRepo = bosituasjongrunnlag,
        )

        val uføreVilkårsvurderingRepo = UføreVilkårsvurderingPostgresRepo(
            dataSource = dataSource,
            uføregrunnlagRepo = uføregrunnlagRepo,
        )

        val formueVilkårsvurderingRepo = FormueVilkårsvurderingPostgresRepo(
            dataSource = dataSource,
            formuegrunnlagPostgresRepo = formuegrunnlagRepo,
        )

        val saksbehandlingRepo = SøknadsbehandlingPostgresRepo(dataSource, uføregrunnlagRepo, fradragsgrunnlag, bosituasjongrunnlag, uføreVilkårsvurderingRepo)

        val revurderingRepo = RevurderingPostgresRepo(
            dataSource,
            uføregrunnlagRepo,
            fradragsgrunnlag,
            bosituasjongrunnlag,
            uføreVilkårsvurderingRepo,
            formueVilkårsvurderingRepo,
            saksbehandlingRepo,
        )
        val vedtakRepo = VedtakPosgresRepo(dataSource, saksbehandlingRepo, revurderingRepo)

        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(dataSource),
            utbetaling = UtbetalingPostgresRepo(dataSource),
            søknad = SøknadPostgresRepo(dataSource),
            hendelseslogg = HendelsesloggPostgresRepo(dataSource),
            sak = SakPostgresRepo(dataSource, saksbehandlingRepo, revurderingRepo, vedtakRepo),
            person = PersonPostgresRepo(dataSource),
            vedtakssnapshot = VedtakssnapshotPostgresRepo(dataSource),
            søknadsbehandling = saksbehandlingRepo,
            revurderingRepo = revurderingRepo,
            vedtakRepo = vedtakRepo,
            grunnlagRepo = grunnlagRepo,
            uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
            formueVilkårsvurderingRepo = formueVilkårsvurderingRepo,
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
)
