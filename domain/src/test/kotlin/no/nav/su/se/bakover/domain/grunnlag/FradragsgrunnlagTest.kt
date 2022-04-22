package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.slåSammenPeriodeOgFradrag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.månedsperiodeFebruar2021
import no.nav.su.se.bakover.test.månedsperiodeJanuar2021
import no.nav.su.se.bakover.test.månedsperiodeMars2021
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FradragsgrunnlagTest {

    private val behandlingsperiode = Periode.create(1.januar(2021), 31.juli(2021))

    @Test
    fun `ugyldig for enkelte fradragstyper`() {
        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.UnderMinstenivå),
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS),
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.ForventetInntekt),
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
        Fradragskategori.values().filterNot {
            listOf(
                FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS).kategori,
                FradragskategoriWrapper(Fradragskategori.ForventetInntekt).kategori,
                FradragskategoriWrapper(Fradragskategori.UnderMinstenivå).kategori,
            ).contains(it)
        }.forEach {
            Grunnlag.Fradragsgrunnlag.tryCreate(
                fradrag = FradragFactory.ny(
                    fradragskategoriWrapper = FradragskategoriWrapper(
                        kategori = it,
                        spesifisertKategori = if (it == Fradragskategori.Annet) "fradragstype som saksbehandler har oppgitt" else null,
                    ),
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
        val oppdatertPeriode = Periode.create(1.januar(2022), 31.desember(2022))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Kontantstøtte),
                månedsbeløp = 200.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).orNull()!!.periode shouldBe oppdatertPeriode
    }

    @Test
    fun `fraOgMed blir kuttet og satt lik stønadsperiode FOM når oppdatertPeriode er etter fraOgMed `() {
        val oppdatertPeriode = Periode.create(1.mai(2021), 31.desember(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Kontantstøtte),
                månedsbeløp = 200.0,
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).orNull()!!.periode shouldBe oppdatertPeriode
    }

    @Test
    fun `tilOgMed blir kuttet og satt lik stønadsperiode TOM når oppdatertPeriode er før tilOgMed `() {
        val oppdatertPeriode = Periode.create(1.januar(2021), 31.august(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Kontantstøtte),
                månedsbeløp = 200.0,
                periode = Periode.create(1.januar(2021), 31.august(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).orNull()!!.periode shouldBe oppdatertPeriode
    }

    @Test
    fun `fradrag med deler av periode i 2022, oppdaterer periode til å gjelde for 2021, får fradragene til å gjelde for hele 2021`() {
        val oppdatertPeriode = Periode.create(1.januar(2021), 31.desember(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Kontantstøtte),
                månedsbeløp = 200.0,
                periode = Periode.create(1.februar(2022), 31.august(2022)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).orNull()!!.periode shouldBe oppdatertPeriode
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, og er lik`() {
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(periode = månedsperiodeFebruar2021)

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `2 fradragsgrunnlag som ikke tilstøter, men er lik`() {
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(periode = månedsperiodeMars2021)

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men fradragstype er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(
            periode = månedsperiodeFebruar2021, type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
        )
        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men månedsbeløp er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(periode = månedsperiodeFebruar2021, månedsbeløp = 300.0)

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som tilstøter, men utenlandsinntekt er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(
            periode = månedsperiodeFebruar2021,
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
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(
            periode = månedsperiodeFebruar2021,
            tilhører = FradragTilhører.EPS,
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 fradragsgrunnlag som  ikke tilstøter, og er ulik`() {
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(
            periode = månedsperiodeMars2021,
            type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
            månedsbeløp = 300.0,
            tilhører = FradragTilhører.EPS,
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `slår sammen fradrag som er like og tilstøtende`() {
        val f1 = lagFradragsgrunnlag(periode = månedsperiodeJanuar2021)
        val f2 = lagFradragsgrunnlag(periode = månedsperiodeFebruar2021)
        val f3 = lagFradragsgrunnlag(
            periode = månedsperiodeMars2021,
            type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
            månedsbeløp = 300.0,
        )

        val actual = listOf(f1, f2, f3).slåSammenPeriodeOgFradrag()
        actual.size shouldBe 2
        actual.first().fradrag shouldBe FradragFactory.ny(
            fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Kontantstøtte),
            månedsbeløp = 200.0,
            periode = Periode.create(1.januar(2021), 28.februar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        actual.last().fradrag shouldBe FradragFactory.ny(
            fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
            månedsbeløp = 300.0,
            periode = månedsperiodeMars2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
    }

    @Test
    fun `fjerner fradrag som tilhører EPS, når vi har bosituasjon uten EPS`() {
        val f1 = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(), opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                månedsbeløp = 100.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
        )

        val f2 = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(), opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.PrivatPensjon),
                månedsbeløp = 100.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        val bosituasjonUtenEPS = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
        )

        listOf(f1, f2).fjernFradragForEPSHvisEnslig(bosituasjonUtenEPS) shouldBe listOf(f2)
    }

    @Test
    fun `fjerner ikke fradrag for EPS, dersom søker bor med EPS`() {
        val f1 = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(), opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                månedsbeløp = 100.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
        )

        val f2 = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(), opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.PrivatPensjon),
                månedsbeløp = 100.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        val bosituasjonUtenEPS = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            fnr = Fnr.generer(),
        )

        listOf(f1, f2).fjernFradragForEPSHvisEnslig(bosituasjonUtenEPS) shouldBe listOf(f1, f2)
    }

    @Test
    fun `fjerner fradrag for EPS for utvalgte perioder og bevarer for resterende`() {
        val fBruker = lagFradragsgrunnlag(
            type = Fradragstype(F.Arbeidsinntekt),
            månedsbeløp = 5_000.0,
            periode = periode2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = lagFradragsgrunnlag(
            type = Fradragstype(F.Arbeidsinntekt),
            månedsbeløp = 10_000.0,
            periode = periode2021,
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
                    type = Fradragstype(F.Arbeidsinntekt),
                    månedsbeløp = 10_000.0,
                    periode = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ) shouldBe true
            it[2].erLik(
                lagFradragsgrunnlag(
                    type = Fradragstype(F.Arbeidsinntekt),
                    månedsbeløp = 10_000.0,
                    periode = Periode.create(1.mars(2021), 31.mai(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ) shouldBe true
            it[3].erLik(
                lagFradragsgrunnlag(
                    type = Fradragstype(F.Arbeidsinntekt),
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
            type = Fradragstype(F.Arbeidsinntekt),
            månedsbeløp = 5_000.0,
            periode = periode2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = lagFradragsgrunnlag(
            type = Fradragstype(F.Arbeidsinntekt),
            månedsbeløp = 10_000.0,
            periode = periode2021,
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
            type = Fradragstype(F.Arbeidsinntekt),
            månedsbeløp = 5_000.0,
            periode = periode2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val fEps = lagFradragsgrunnlag(
            type = Fradragstype(F.Arbeidsinntekt),
            månedsbeløp = 10_000.0,
            periode = periode2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        listOf(fBruker, fEps).fjernFradragEPS(listOf(Periode.create(1.januar(2023), 31.desember(2023)))).let {
            it shouldBe listOf(fBruker, fEps.copy(id = it[1].id))
        }
    }

    private fun lagFradragsgrunnlag(
        opprettet: Tidspunkt = fixedTidspunkt,
        type: FradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Kontantstøtte),
        månedsbeløp: Double = 200.0,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører = FradragTilhører.BRUKER,
    ): Grunnlag.Fradragsgrunnlag {
        return Grunnlag.Fradragsgrunnlag.create(
            opprettet = opprettet,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = type,
                månedsbeløp = månedsbeløp,
                periode = periode,
                utenlandskInntekt = utenlandskInntekt,
                tilhører = tilhører,
            ),
        )
    }
}
