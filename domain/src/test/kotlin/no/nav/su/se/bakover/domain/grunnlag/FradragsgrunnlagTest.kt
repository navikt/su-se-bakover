package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.slåSammenPeriodeOgFradrag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FradragsgrunnlagTest {

    private val behandlingsperiode = Periode.create(1.januar(2021), 31.juli(2021))

    @Test
    fun `ugyldig for enkelte fradragstyper`() {
        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.UnderMinstenivå,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.BeregnetFradragEPS,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()
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
            Grunnlag.Fradragsgrunnlag.tryCreate(
                fradrag = FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.from(it, null),
                    månedsbeløp = 150.0,
                    periode = behandlingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                opprettet = fixedTidspunkt,
            ).isRight() shouldBe true
        }
    }

    @Test
    fun `fradrag med periode som er lik stønadsperiode, blir oppdatert til å gjelde for hele stønadsperioden`() {
        val oppdatertPeriode = år(2022)
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
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

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode
    }

    @Test
    fun `fraOgMed blir kuttet og satt lik stønadsperiode FOM når oppdatertPeriode er etter fraOgMed `() {
        val oppdatertPeriode = Periode.create(1.mai(2021), 31.desember(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
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

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode
    }

    @Test
    fun `tilOgMed blir kuttet og satt lik stønadsperiode TOM når oppdatertPeriode er før tilOgMed `() {
        val oppdatertPeriode = Periode.create(1.januar(2021), 31.august(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
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

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode
    }

    @Test
    fun `fradrag med deler av periode i 2022, oppdaterer periode til å gjelde for 2021, får fradragene til å gjelde for hele 2021`() {
        val oppdatertPeriode = år(2021)
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
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

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail().periode shouldBe oppdatertPeriode
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, og er lik`() {
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(periode = februar(2021))

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `2 fradragsgrunnlag som ikke tilstøter, men er lik`() {
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(periode = mars(2021))

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men fradragstype er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(
            periode = februar(2021),
            type = Fradragstype.Sosialstønad,
        )
        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men månedsbeløp er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(periode = februar(2021), månedsbeløp = 300.0)

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men utenlandsinntekt er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(
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
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(
            periode = februar(2021),
            tilhører = FradragTilhører.EPS,
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som  ikke tilstøter, og er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(
            periode = mars(2021),
            type = Fradragstype.Sosialstønad,
            månedsbeløp = 300.0,
            tilhører = FradragTilhører.EPS,
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `slår sammen fradrag som er like og tilstøtende`() {
        val f1 = lagFradragsgrunnlag(periode = januar(2021))
        val f2 = lagFradragsgrunnlag(periode = februar(2021))
        val f3 = lagFradragsgrunnlag(
            periode = mars(2021),
            type = Fradragstype.Sosialstønad,
            månedsbeløp = 300.0,
        )

        val actual = listOf(f1, f2, f3).slåSammenPeriodeOgFradrag(clock = fixedClock)
        actual.size shouldBe 2
        actual.first().fradrag shouldBe FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.Kontantstøtte,
            månedsbeløp = 200.0,
            periode = Periode.create(1.januar(2021), 28.februar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        actual.last().fradrag shouldBe FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.Sosialstønad,
            månedsbeløp = 300.0,
            periode = mars(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
    }

    @Test
    fun `fjerner fradrag som tilhører EPS, når vi har bosituasjon uten EPS`() {
        val f1 = Grunnlag.Fradragsgrunnlag.create(
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

        val f2 = Grunnlag.Fradragsgrunnlag.create(
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

        val bosituasjonUtenEPS = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
        )

        listOf(f1, f2).fjernFradragForEPSHvisEnslig(bosituasjonUtenEPS) shouldBe listOf(f2)
    }

    @Test
    fun `fjerner ikke fradrag for EPS, dersom søker bor med EPS`() {
        val f1 = Grunnlag.Fradragsgrunnlag.create(
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

        val f2 = Grunnlag.Fradragsgrunnlag.create(
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

        val bosituasjonUtenEPS = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            fnr = Fnr.generer(),
        )

        listOf(f1, f2).fjernFradragForEPSHvisEnslig(bosituasjonUtenEPS) shouldBe listOf(f1, f2)
    }

    @Test
    fun `fjerner fradrag for EPS for utvalgte perioder og bevarer for resterende`() {
        val fBruker = lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 5_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = lagFradragsgrunnlag(
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
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10_000.0,
                    periode = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ) shouldBe true
            it[2].erLik(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10_000.0,
                    periode = Periode.create(1.mars(2021), 31.mai(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ) shouldBe true
            it[3].erLik(
                lagFradragsgrunnlag(
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
        val fBruker = lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 5_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = lagFradragsgrunnlag(
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
        val fBruker = lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 5_000.0,
            periode = år(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = lagFradragsgrunnlag(
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

    private fun lagFradragsgrunnlag(
        opprettet: Tidspunkt = fixedTidspunkt,
        type: Fradragstype = Fradragstype.Kontantstøtte,
        månedsbeløp: Double = 200.0,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører = FradragTilhører.BRUKER,
    ): Grunnlag.Fradragsgrunnlag {
        return Grunnlag.Fradragsgrunnlag.create(
            opprettet = opprettet,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = type,
                månedsbeløp = månedsbeløp,
                periode = periode,
                utenlandskInntekt = utenlandskInntekt,
                tilhører = tilhører,
            ),
        )
    }
}
