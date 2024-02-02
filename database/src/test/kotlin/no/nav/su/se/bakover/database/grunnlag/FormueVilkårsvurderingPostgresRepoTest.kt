package no.nav.su.se.bakover.database.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withTransaction
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkår.formuevilkårUtenEps0Innvilget
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.Verdier
import vilkår.formue.domain.VurderingsperiodeFormue
import java.util.UUID

internal class FormueVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter IkkeVurdert`() {
        withMigratedDb { dataSource ->
            val repo = FormueVilkårsvurderingPostgresRepo(FormuegrunnlagPostgresRepo(dbMetricsStub), dbMetricsStub)
            val behandlingId = SøknadsbehandlingId.generer()
            val vilkår = formuevilkårIkkeVurdert()
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe false
                    it.vurdering shouldBe Vurdering.Uavklart
                }
            }
        }
    }

    private fun formuegrunnlag(
        periode: Periode,
        søkersFormue: Verdier = Verdier.create(
            verdiIkkePrimærbolig = 9,
            verdiEiendommer = 10,
            verdiKjøretøy = 11,
            innskudd = 12,
            verdipapir = 13,
            pengerSkyldt = 14,
            kontanter = 15,
            depositumskonto = 10,
        ),
        epsFormue: Verdier? = Verdier.create(
            verdiIkkePrimærbolig = 1,
            verdiEiendommer = 2,
            verdiKjøretøy = 3,
            innskudd = 4,
            verdipapir = 5,
            pengerSkyldt = 6,
            kontanter = 6,
            depositumskonto = 1,
        ),
    ): Formuegrunnlag {
        return Formuegrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            epsFormue = epsFormue,
            søkersFormue = søkersFormue,
            behandlingsPeriode = periode,
        )
    }

    @Test
    fun `lagrer og henter innvilget med epsFormue`() {
        val periode = år(2021)

        withMigratedDb { dataSource ->
            val repo = FormueVilkårsvurderingPostgresRepo(FormuegrunnlagPostgresRepo(dbMetricsStub), dbMetricsStub)
            val behandlingId = SøknadsbehandlingId.generer()
            val vilkår = FormueVilkår.Vurdert.createFromGrunnlag(
                grunnlag = nonEmptyListOf(formuegrunnlag(periode)),
            )
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe true
                    it.vurdering shouldBe Vurdering.Innvilget
                }
            }
        }
    }

    @Test
    fun `lagrer og henter innvilget uten epsFormue`() {
        val periode = år(2021)

        withMigratedDb { dataSource ->
            val repo = FormueVilkårsvurderingPostgresRepo(FormuegrunnlagPostgresRepo(dbMetricsStub), dbMetricsStub)
            val behandlingId = SøknadsbehandlingId.generer()
            val vilkår = FormueVilkår.Vurdert.createFromGrunnlag(
                grunnlag = nonEmptyListOf(formuegrunnlag(periode = periode, epsFormue = null)),
            )
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe true
                    it.vurdering shouldBe Vurdering.Innvilget
                }
            }
        }
    }

    @Test
    fun `lagrer og henter avslag med for høy epsFormue`() {
        val periode = år(2021)

        withMigratedDb { dataSource ->
            val repo = FormueVilkårsvurderingPostgresRepo(FormuegrunnlagPostgresRepo(dbMetricsStub), dbMetricsStub)
            val behandlingId = SøknadsbehandlingId.generer()
            val vilkår = FormueVilkår.Vurdert.createFromGrunnlag(
                grunnlag = nonEmptyListOf(
                    formuegrunnlag(
                        periode = periode,
                        epsFormue = Verdier.create(
                            verdiIkkePrimærbolig = 1,
                            verdiEiendommer = 2,
                            verdiKjøretøy = 3,
                            innskudd = 4,
                            verdipapir = 5,
                            pengerSkyldt = 6,
                            kontanter = 70000,
                            depositumskonto = 3,
                        ),
                    ),
                ),
            )
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe true
                    it.erInnvilget shouldBe false
                    it.vurdering shouldBe Vurdering.Avslag
                }
            }
        }
    }

    @Test
    fun `lagrer og henter uavklart uten epsFormue`() {
        val periode = år(2021)

        withMigratedDb { dataSource ->
            val repo = FormueVilkårsvurderingPostgresRepo(FormuegrunnlagPostgresRepo(dbMetricsStub), dbMetricsStub)
            val behandlingId = SøknadsbehandlingId.generer()
            val vilkår = FormueVilkår.Vurdert.createFromVilkårsvurderinger(
                nonEmptyListOf(
                    VurderingsperiodeFormue.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        // TODO(satsfactory_formue) jah: Man kan ikke opprette formuevilkår/vurdering/grunnlag som uavklart lenger. Kan sjekke at det ikke finnes spor av denne i basen og fjerne muligheten?
                        vurdering = Vurdering.Uavklart,
                        grunnlag = formuegrunnlag(periode = periode, epsFormue = null),
                        periode = periode,
                    ),
                ),
            )
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe false
                    it.vurdering shouldBe Vurdering.Uavklart
                }
            }
        }
    }

    @Test
    fun `sletter grunnlag hvis vurdering går fra vurdert til ikke vurdert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val formuegrunnlagPostgresRepo = FormuegrunnlagPostgresRepo(dbMetricsStub)
            val formueVilkårsvurderingPostgresRepo =
                FormueVilkårsvurderingPostgresRepo(formuegrunnlagPostgresRepo, dbMetricsStub)
            val (vilkår, grunnlag) = formuevilkårUtenEps0Innvilget(
                bosituasjon = bosituasjongrunnlagEnslig(periode = år(2021)),
            ).let { it to it.grunnlag }

            dataSource.withTransaction { session ->
                formueVilkårsvurderingPostgresRepo.lagre(søknadsbehandling.id, vilkår, session)
                formueVilkårsvurderingPostgresRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
                formueVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    formuevilkårIkkeVurdert(),
                    session,
                )
                formueVilkårsvurderingPostgresRepo.hent(
                    behandlingId = søknadsbehandling.id,
                    session = session,
                ) shouldBe formuevilkårIkkeVurdert()

                formuegrunnlagPostgresRepo.hentFormuegrunnlag(
                    formuegrunnlagId = grunnlag.first().id,
                    session = session,
                ) shouldBe null
            }
        }
    }
}
