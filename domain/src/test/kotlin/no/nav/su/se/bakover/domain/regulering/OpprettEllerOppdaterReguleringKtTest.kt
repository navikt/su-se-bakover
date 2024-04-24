package no.nav.su.se.bakover.domain.regulering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nyEksternvedtakEndring
import no.nav.su.se.bakover.test.nyEksternvedtakRegulering
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import java.math.BigDecimal
import java.util.UUID

internal class OpprettEllerOppdaterReguleringKtTest {

    @Test
    fun `kan ikke regulere sak uten vedtak`() {
        val sakMedÅpenSøknadsbehandling = nySøknadsbehandlingMedStønadsperiode().first
        sakMedÅpenSøknadsbehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement.empty(),
            BigDecimal("1.064076"),
        )
            .shouldBe(Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left())
    }

    @Test
    fun `hvis supplementet ikke inneholder fradrag for et gitt fradrag (som krever manuell regulering), blir den manuell`() {
        val sakUtenÅpenBehandling = (
            iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 995.0,
                            periode = stønadsperiode2021.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
            ).first
        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement.empty(),
            BigDecimal("1.064076"),
        ).getOrFail()

        actual.let {
            it.reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
                        fradragskategori = Fradragstype.Kategori.Alderspensjon,
                        fradragTilhører = FradragTilhører.BRUKER,
                        begrunnelse = "Fradraget til BRUKER: Alderspensjon påvirkes av samme sats/G-verdi endring som SU. Vi mangler supplement for dette fradraget og derfor går det til manuell regulering.",
                    ),
                ),
            )
            it.grunnlagsdata.fradragsgrunnlag.single().månedsbeløp shouldBe 995
        }
    }

    @Test
    fun `dersom det eksisterer 2 reguleringsvedtak går den til manuell behandling`() {
        val sakUtenÅpenBehandling = (
            iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 1000.0,
                            periode = januar(2021)..juni(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 995.0,
                            periode = juli(2021)..desember(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
            ).first

        val supplementFor = nyReguleringssupplementFor(
            fnr = sakUtenÅpenBehandling.fnr,
            ReguleringssupplementFor.PerType(
                kategori = Fradragstype.Alderspensjon.kategori,
                vedtak = nonEmptyListOf(
                    nyEksternvedtakRegulering(tilOgMed = 31.mai(2021)),
                    nyEksternvedtakRegulering(fraOgMed = 1.juni(2021), null),
                    nyEksternvedtakEndring(),
                ),
            ),
        )
        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement(listOf(supplementFor)),
            BigDecimal("1.064076"),
        )
        actual.getOrFail().let {
            it.reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
                        fradragskategori = Fradragstype.Kategori.Alderspensjon,
                        fradragTilhører = FradragTilhører.BRUKER,
                        begrunnelse = "Fradraget til BRUKER: Alderspensjon er delt opp i flere perioder. Disse går foreløpig til manuell regulering.",

                    ),
                ),
            )
            it.grunnlagsdata.fradragsgrunnlag.size shouldBe 2
            it.grunnlagsdata.fradragsgrunnlag.first().månedsbeløp shouldBe 1000
            it.grunnlagsdata.fradragsgrunnlag.last().månedsbeløp shouldBe 995.0
        }
    }

    @Test
    fun `hvis regulering inneholder utenlandskfradrag som må reguleres, går den til manuell behandling`() {
        val sakUtenÅpenBehandling = (
            iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 1000.0,
                            periode = stønadsperiode2021.periode,
                            utenlandskInntekt = UtenlandskInntekt.create(100, "SEK", 1.0),
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
            ).first
        val supplementFor = nyReguleringssupplementFor(
            fnr = sakUtenÅpenBehandling.fnr,
            ReguleringssupplementFor.PerType(
                kategori = Fradragstype.Alderspensjon.kategori,
                vedtak = nonEmptyListOf(
                    nyEksternvedtakRegulering(),
                    nyEksternvedtakEndring(),
                ),
            ),
        )

        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement(listOf(supplementFor)),
            BigDecimal("1.064076"),
        )
        actual.getOrFail().let {
            it.reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
                        fradragskategori = Fradragstype.Kategori.Alderspensjon,
                        fradragTilhører = FradragTilhører.BRUKER,
                        begrunnelse = "Fradraget er utenlandsinntekt og går til manuell regulering",
                    ),
                ),
            )
            it.grunnlagsdata.fradragsgrunnlag.size shouldBe 1
            it.grunnlagsdata.fradragsgrunnlag.first().månedsbeløp shouldBe 1000
        }
    }

    @Test
    fun `bevarer fradrag som ikke har blitt endret av supplementet`() {
        val sakUtenÅpenBehandling = (
            iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 995.0,
                            periode = stønadsperiode2021.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Arbeidsavklaringspenger,
                            månedsbeløp = 1000.0,
                            periode = stønadsperiode2021.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
            ).first

        val supplementFor = nyReguleringssupplementFor(
            fnr = sakUtenÅpenBehandling.fnr,
            ReguleringssupplementFor.PerType(
                kategori = Fradragstype.Alderspensjon.kategori,
                vedtak = nonEmptyListOf(
                    nyEksternvedtakRegulering(beløp = 1050),
                    nyEksternvedtakEndring(beløp = 995),
                ),
            ),
        )

        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement(listOf(supplementFor)),
            BigDecimal("1.064076"),
        )
        actual.getOrFail().let {
            it.reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
                        fradragskategori = Fradragstype.Kategori.Arbeidsavklaringspenger,
                        fradragTilhører = FradragTilhører.BRUKER,
                        begrunnelse = "Vi fant et supplement for BRUKER, men ikke for Arbeidsavklaringspenger.",
                    ),
                ),
            )
            it.grunnlagsdata.fradragsgrunnlag.size shouldBe 2
            it.grunnlagsdata.fradragsgrunnlag.first().månedsbeløp shouldBe 1050
            it.grunnlagsdata.fradragsgrunnlag.last().månedsbeløp shouldBe 1000
        }
    }

    @Test
    fun `hvis differansen mellom supplementet og den forventede økningen er mer enn 10, går den til manuell`() {
        val sakUtenÅpenBehandling = (
            iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 1000.0,
                            periode = stønadsperiode2021.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
            ).first

        val supplementFor = nyReguleringssupplementFor(
            fnr = sakUtenÅpenBehandling.fnr,
            ReguleringssupplementFor.PerType(
                kategori = Fradragstype.Alderspensjon.kategori,
                vedtak = nonEmptyListOf(nyEksternvedtakRegulering(beløp = 1200), nyEksternvedtakEndring()),
            ),
        )
        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement(listOf(supplementFor)),
            BigDecimal("1.064076"),
        )
        actual.getOrFail().let {
            it.reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BeløpErStørreEnForventet(
                        fradragskategori = Fradragstype.Kategori.Alderspensjon,
                        fradragTilhører = FradragTilhører.BRUKER,
                        begrunnelse = "Vi forventet at beløpet skulle være 1064.08 etter regulering, men det var 1200. Vi aksepterer en differanse på 10, men den var 135.92",
                        eksterntBeløpEtterRegulering = BigDecimal(1200),
                        forventetBeløpEtterRegulering = BigDecimal("1064.08"),
                    ),
                ),
            )
            it.grunnlagsdata.fradragsgrunnlag.size shouldBe 1
            it.grunnlagsdata.fradragsgrunnlag.first().månedsbeløp shouldBe 1000
        }
    }

    @Test
    fun `regulering blir automatisk behandlet dersom den ikke har noe fradrag eller supplement`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre()).first
        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement.empty(),
            BigDecimal("1.064076"),
        ).getOrFail()
        actual.reguleringstype shouldBe Reguleringstype.AUTOMATISK
    }

    @Test
    fun `regulering blir automatisk behandlet, dersom periodene matcher, og beløpet er innenfor 10 kroner av omregningsfaktor`() {
        val sakUtenÅpenBehandling = (
            iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 995.0,
                            periode = stønadsperiode2021.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
            ).first

        val supplementFor = nyReguleringssupplementFor(
            fnr = sakUtenÅpenBehandling.fnr,
            ReguleringssupplementFor.PerType(
                kategori = Fradragstype.Alderspensjon.kategori,
                vedtak = nonEmptyListOf(nyEksternvedtakRegulering(beløp = 1050), nyEksternvedtakEndring(beløp = 995)),
            ),
        )
        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement(listOf(supplementFor)),
            BigDecimal("1.064076"),
        )
        actual.getOrFail().let {
            it.reguleringstype shouldBe Reguleringstype.AUTOMATISK
            it.grunnlagsdata.fradragsgrunnlag.single().månedsbeløp shouldBe 1050
        }
    }
}
