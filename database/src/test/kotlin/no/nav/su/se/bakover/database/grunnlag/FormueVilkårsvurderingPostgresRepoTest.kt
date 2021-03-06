package no.nav.su.se.bakover.database.grunnlag

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FormueVilkårsvurderingPostgresRepoTest {

    private val datasource = EmbeddedDatabase.instance()
    private val repo = FormueVilkårsvurderingPostgresRepo(
        datasource,
        FormuegrunnlagPostgresRepo(),
    )

    @Test
    fun `lagrer og henter IkkeVurdert`() {
        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.IkkeVurdert
            repo.lagre(behandlingId, vilkår)
            datasource.withSession { session ->
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe false
                    it.resultat shouldBe Resultat.Uavklart
                }
            }
        }
    }

    private fun formuegrunnlag(
        periode: Periode,
        epsFormue: Formuegrunnlag.Verdier? = null,
    ) = NonEmptyList.fromListUnsafe(
        listOf(
            Formuegrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
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
                    opprettet = Tidspunkt.now(),
                    periode = periode,
                    fnr = FnrGenerator.random(),
                    begrunnelse = "i87i78i78i87i",
                ),
                behandlingsPeriode = periode,
            ),
        ),
    )

    @Test
    fun `lagrer og henter innvilget med epsFormue`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))

        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromGrunnlag(
                grunnlag = formuegrunnlag(
                    periode = periode,
                    epsFormue = Formuegrunnlag.Verdier.create(
                        verdiIkkePrimærbolig = 1,
                        verdiEiendommer = 2,
                        verdiKjøretøy = 3,
                        innskudd = 4,
                        verdipapir = 5,
                        pengerSkyldt = 6,
                        kontanter = 7,
                        depositumskonto = 3,
                    ),
                ),
            )
            repo.lagre(behandlingId, vilkår)
            datasource.withSession { session ->
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

        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromGrunnlag(
                grunnlag = formuegrunnlag(
                    periode = periode,
                    epsFormue = null,
                ),
            )
            repo.lagre(behandlingId, vilkår)
            datasource.withSession { session ->
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

        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromGrunnlag(
                grunnlag = formuegrunnlag(
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
            )
            repo.lagre(behandlingId, vilkår)
            datasource.withSession { session ->
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

        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                nonEmptyListOf(
                    Vurderingsperiode.Formue.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Uavklart,
                        grunnlag = formuegrunnlag(
                            periode = periode,
                            epsFormue = null,
                        ).first(),
                        periode = periode,
                    ),
                ),
            )
            repo.lagre(behandlingId, vilkår)
            datasource.withSession { session ->
                repo.hent(behandlingId, session).let {
                    it shouldBe vilkår
                    it.erAvslag shouldBe false
                    it.erInnvilget shouldBe false
                    it.resultat shouldBe Resultat.Uavklart
                }
            }
        }
    }
}
