package no.nav.su.se.bakover.database.grunnlag

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuevilkårUtenEps0Innvilget
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FormueVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter IkkeVurdert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.formueVilkårsvurderingPostgresRepo
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.IkkeVurdert
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe false
                    it.resultat shouldBe Resultat.Uavklart
                }
            }
        }
    }

    private fun formuegrunnlagz(
        periode: Periode,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            fnr = Fnr.generer(),
            begrunnelse = "i87i78i78i87i",
        ),
        søkersFormue: Formuegrunnlag.Verdier = Formuegrunnlag.Verdier.create(
            verdiIkkePrimærbolig = 9,
            verdiEiendommer = 10,
            verdiKjøretøy = 11,
            innskudd = 12,
            verdipapir = 13,
            pengerSkyldt = 14,
            kontanter = 15,
            depositumskonto = 10,
        ),
        epsFormue: Formuegrunnlag.Verdier? = Formuegrunnlag.Verdier.create(
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
            begrunnelse = "dfgdfgdfgsdfgdfg",
            bosituasjon = bosituasjon,
            behandlingsPeriode = periode,
        )
    }

    private fun formuegrunnlag(
        periode: Periode,
        epsFormue: Formuegrunnlag.Verdier? = null,
    ) = NonEmptyList.fromListUnsafe(
        listOf(
            Formuegrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                epsFormue = epsFormue,
                søkersFormue = Formuegrunnlag.Verdier.create(
                    verdiIkkePrimærbolig = 9,
                    verdiEiendommer = 10,
                    verdiKjøretøy = 11,
                    innskudd = 12,
                    verdipapir = 13,
                    pengerSkyldt = 14,
                    kontanter = 15,
                    depositumskonto = 10,
                ),
                begrunnelse = "dfgdfgdfgsdfgdfg",
                bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    fnr = Fnr.generer(),
                    begrunnelse = "i87i78i78i87i",
                ),
                behandlingsPeriode = periode,
            ),
        ),
    )

    @Test
    fun `lagrer og henter innvilget med epsFormue`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.formueVilkårsvurderingPostgresRepo
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromGrunnlag(
                grunnlag = nonEmptyListOf(formuegrunnlagz(periode)),
            )
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe true
                    it.resultat shouldBe Resultat.Innvilget
                }
            }
        }
    }

    @Test
    fun `lagrer og henter innvilget uten epsFormue`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.formueVilkårsvurderingPostgresRepo
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromGrunnlag(
                grunnlag = nonEmptyListOf(
                    formuegrunnlagz(
                        periode = periode,
                        bosituasjon = bosituasjongrunnlagEnslig(periode = periode),
                        epsFormue = null,
                    ),
                ),
            )
            dataSource.withTransaction { session ->
                repo.lagre(behandlingId, vilkår, session)
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe true
                    it.resultat shouldBe Resultat.Innvilget
                }
            }
        }
    }

    @Test
    fun `lagrer og henter avslag med for høy epsFormue`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.formueVilkårsvurderingPostgresRepo
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromGrunnlag(
                grunnlag = nonEmptyListOf(
                    formuegrunnlagz(
                        periode = periode,
                        epsFormue = Formuegrunnlag.Verdier.create(
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
                    it.resultat shouldBe Resultat.Avslag
                }
            }
        }
    }

    @Test
    fun `lagrer og henter uavklart uten epsFormue`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))

        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.formueVilkårsvurderingPostgresRepo
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                nonEmptyListOf(
                    Vurderingsperiode.Formue.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Uavklart,
                        grunnlag = formuegrunnlagz(
                            periode = periode,
                            bosituasjon = bosituasjongrunnlagEnslig(periode = periode),
                            epsFormue = null
                        ),
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
                    it.resultat shouldBe Resultat.Uavklart
                }
            }
        }
    }

    @Test
    fun `sletter grunnlag hvis vurdering går fra vurdert til ikke vurdert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val (vilkår, grunnlag) = formuevilkårUtenEps0Innvilget(
                bosituasjon = bosituasjongrunnlagEnslig(periode = periode2021),
            ).let { it to it.grunnlag }

            dataSource.withTransaction { session ->
                testDataHelper.formueVilkårsvurderingPostgresRepo.lagre(søknadsbehandling.id, vilkår, session)
                testDataHelper.formueVilkårsvurderingPostgresRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
                testDataHelper.formueVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    Vilkår.Formue.IkkeVurdert,
                    session,
                )
                testDataHelper.formueVilkårsvurderingPostgresRepo.hent(
                    behandlingId = søknadsbehandling.id,
                    session = session,
                ) shouldBe Vilkår.Formue.IkkeVurdert

                testDataHelper.formuegrunnlagPostgresRepo.hentFormuegrunnlag(
                    formuegrunnlagId = grunnlag.first().id,
                    session = session,
                ) shouldBe null
            }
        }
    }
}
