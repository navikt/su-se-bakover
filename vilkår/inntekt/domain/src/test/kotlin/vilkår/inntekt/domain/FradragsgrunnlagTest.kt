package vilkår.inntekt.domain

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import org.junit.jupiter.api.Test
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import vilkår.vurderinger.domain.fjernFradragEPS
import vilkår.vurderinger.domain.fjernFradragForEPSHvisEnslig
import java.util.UUID

internal class FradragsgrunnlagTest {

    private val behandlingsperiode = Periode.create(1.januar(2021), 31.juli(2021))

    @Test
    fun `ugyldig for enkelte fradragstyper`() {
        Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.UnderMinstenivå,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.BeregnetFradragEPS,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()
    }

    @Test
    fun `kan lage gyldige fradragsgrunnlag`() {
        Fradragstype.Kategori.entries.filterNot {
            listOf(
                Fradragstype.Kategori.BeregnetFradragEPS,
                Fradragstype.Kategori.ForventetInntekt,
                Fradragstype.Kategori.UnderMinstenivå,
                Fradragstype.Kategori.Annet,
            ).contains(it)
        }.forEach {
            Fradragsgrunnlag.tryCreate(
                fradrag = FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.from(it, null),
                    månedsbeløp = 150.0,
                    periode = behandlingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                opprettet = fixedTidspunkt,
            ).shouldBeRight()
        }
    }

    @Test
    fun `fradrag med periode som er lik stønadsperiode, blir oppdatert til å gjelde for hele stønadsperioden`() {
        val oppdatertPeriode = Stønadsperiode.create(år(2022))
        val fradragsgrunnlag = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = år(2021),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterStønadsperiode(
            nyStønadsperiode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode.periode
    }

    @Test
    fun `fraOgMed blir kuttet og satt lik stønadsperiode FOM når oppdatertPeriode er etter fraOgMed `() {
        val oppdatertPeriode = Stønadsperiode.create(Periode.create(1.mai(2021), 31.desember(2021)))
        val fradragsgrunnlag = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterStønadsperiode(
            nyStønadsperiode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode.periode
    }

    @Test
    fun `tilOgMed blir kuttet og satt lik stønadsperiode TOM når oppdatertPeriode er før tilOgMed `() {
        val oppdatertPeriode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.august(2021)))
        val fradragsgrunnlag = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = Periode.create(1.januar(2021), 31.august(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterStønadsperiode(
            nyStønadsperiode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode.periode
    }

    @Test
    fun `fradrag med deler av periode i 2022, oppdaterer periode til å gjelde for 2021, får fradragene til å gjelde for hele 2021`() {
        val oppdatertPeriode = Stønadsperiode.create(år(2021))
        val fradragsgrunnlag = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = Periode.create(1.februar(2022), 31.august(2022)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterStønadsperiode(
            nyStønadsperiode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode.periode
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, og er lik`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(periode = februar(2021))

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `2 fradragsgrunnlag som ikke tilstøter, men er lik`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(periode = mars(2021))

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men fradragstype er ulik`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(
            periode = februar(2021),
            type = Fradragstype.Sosialstønad,
        )
        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men månedsbeløp er ulik`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(periode = februar(2021), månedsbeløp = 300.0)

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men utenlandsinntekt er ulik`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(
            periode = februar(2021),
            utenlandskInntekt = UtenlandskInntekt.create(
                beløpIUtenlandskValuta = 9000,
                valuta = "its over 9000",
                kurs = 9001.0,
            ),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men tilhører er ulik`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(
            periode = februar(2021),
            tilhører = FradragTilhører.EPS,
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som  ikke tilstøter, og er ulik`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(
            periode = mars(2021),
            type = Fradragstype.Sosialstønad,
            månedsbeløp = 300.0,
            tilhører = FradragTilhører.EPS,
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `fjerner fradrag som tilhører EPS, når vi har bosituasjon uten EPS`() {
        val f1 = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Sosialstønad,
                månedsbeløp = 100.0,
                periode = år(2021),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
        )

        val f2 = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.PrivatPensjon,
                månedsbeløp = 100.0,
                periode = år(2021),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        val bosituasjonUtenEPS = Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        )

        listOf(f1, f2).fjernFradragForEPSHvisEnslig(bosituasjonUtenEPS) shouldBe listOf(f2)
    }

    @Test
    fun `fjerner ikke fradrag for EPS, dersom søker bor med EPS`() {
        val f1 = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Sosialstønad,
                månedsbeløp = 100.0,
                periode = år(2021),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
        )

        val f2 = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.PrivatPensjon,
                månedsbeløp = 100.0,
                periode = år(2021),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        val bosituasjonUtenEPS = Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            fnr = Fnr.generer(),
        )

        listOf(f1, f2).fjernFradragForEPSHvisEnslig(bosituasjonUtenEPS) shouldBe listOf(f1, f2)
    }

    @Test
    fun `fjerner fradrag for EPS for utvalgte perioder og bevarer for resterende`() {
        val fBruker = nyFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 5_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = nyFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        listOf(fBruker, fEps).fjernFradragEPS(
            listOf(
                februar(2021),
                juni(2021),
            ),
        ).let {
            it[0] shouldBe fBruker
            it[1].erLik(
                nyFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10_000.0,
                    periode = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ) shouldBe true
            it[2].erLik(
                nyFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10_000.0,
                    periode = Periode.create(1.mars(2021), 31.mai(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ) shouldBe true
            it[3].erLik(
                nyFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10_000.0,
                    periode = Periode.create(1.juli(2021), 31.desember(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ) shouldBe true
        }
    }

    @Test
    fun `fjerning av fradrag for EPS uten spesifisert periode`() {
        val fBruker = nyFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 5_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = nyFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        listOf(fBruker, fEps).fjernFradragEPS(emptyList()).let {
            it shouldBe listOf(fBruker, fEps.copy(id = it[1].id))
        }
    }

    @Test
    fun `fjerning av fradrag for EPS perioder som ikke overlapper med fradraget`() {
        val fBruker = nyFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 5_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = nyFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        listOf(fBruker, fEps).fjernFradragEPS(listOf(år(2023))).let {
            it shouldBe listOf(fBruker, fEps.copy(id = it[1].id))
        }
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlag = nyFradragsgrunnlag()
        grunnlag.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(grunnlag, Fradragsgrunnlag::id)
            it.id shouldNotBe grunnlag.id
        }
    }
}
