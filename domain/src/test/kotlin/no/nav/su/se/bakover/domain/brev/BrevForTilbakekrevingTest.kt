package no.nav.su.se.bakover.domain.brev

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselTilbakekrevingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.requireType
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgaBrukersformue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate

class BrevForTilbakekrevingTest {

    private fun expectedTilbakekrevingAvPenger(
        revurdering: Revurdering,
        attestant: NavIdentBruker.Attestant?,
        tilbakekreving: Tilbakekreving,
    ): IverksettRevurderingDokumentCommand.TilbakekrevingAvPenger {
        val expectedSatsoversikt = Satsoversikt(
            perioder = listOf(
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.01.2021",
                    tilOgMed = "30.04.2021",
                    sats = "høy",
                    satsBeløp = 20946,
                    satsGrunn = "ENSLIG",
                ),
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.05.2021",
                    tilOgMed = "31.12.2021",
                    sats = "høy",
                    satsBeløp = 21989,
                    satsGrunn = "ENSLIG",
                ),
            ),
        )
        return IverksettRevurderingDokumentCommand.TilbakekrevingAvPenger(
            ordinærtRevurderingBrev = IverksettRevurderingDokumentCommand.Inntekt(
                fødselsnummer = revurdering.fnr,
                saksnummer = revurdering.saksnummer,
                saksbehandler = revurdering.saksbehandler,
                attestant = attestant,
                beregning = revurdering.beregning!!,
                fritekst = (revurdering.brevvalgRevurdering as BrevvalgRevurdering.Valgt.SendBrev).fritekst!!,
                harEktefelle = false,
                forventetInntektStørreEnn0 = false,
                satsoversikt = expectedSatsoversikt,
            ),
            satsoversikt = expectedSatsoversikt,
            tilbakekreving = tilbakekreving,
        )
    }

    private fun expectedForhåndsvarselTilbakekrevingDokumentCommand(
        revurdering: Revurdering,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
        bruttoTilbakekreving: Int = 32086,
        tilbakekreving: Tilbakekreving,
    ) = ForhåndsvarselTilbakekrevingDokumentCommand(
        fødselsnummer = revurdering.fnr,
        saksnummer = revurdering.saksnummer,
        saksbehandler = utførtAv,
        fritekst = fritekst,
        // TODO jah: Det er utbetalt 5k for mye over 6mnd, så dette skal bli 30k.
        //  Fix TolketDetalj i simuleringsemulatoren. Det kan virke som Ytelse debet er 15946 (20946-5000), men skulle egentlig vært (21989-5000)
        bruttoTilbakekreving = bruttoTilbakekreving,
        tilbakekreving = tilbakekreving,
    )

    @Nested
    inner class RevurderingUtenOpphør {

        @Test
        fun `simulert revurdering forhåndsvarsel med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())
            simulertInnvilgetTilbakekreving(clock = clock).let { (_, revurdering) ->
                val utførtAv = NavIdentBruker.Saksbehandler("utførtAv")
                val fritekst = "simulertRevurderingForhåndsvarselMedTilbakekrevingFritekst"

                revurdering.lagForhåndsvarsel(
                    fritekst = fritekst,
                    utførtAv = utførtAv,
                ).getOrFail() shouldBe expectedForhåndsvarselTilbakekrevingDokumentCommand(
                    revurdering = revurdering,
                    utførtAv = utførtAv,
                    fritekst = fritekst,
                    tilbakekreving = expectedTilbakekrevingUtenOpphør(),
                )
                revurdering.lagDokumentKommando(
                    satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock = clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering = revurdering,
                    tilbakekreving = expectedTilbakekrevingUtenOpphør(),
                    attestant = null,
                )
            }
        }

        @Test
        fun `til attestering brev med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())

            innvilgetTilbakekrevingTilAttestering(clock = clock).let { (_, revurdering) ->
                revurdering.lagDokumentKommando(
                    satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering = revurdering,
                    tilbakekreving = expectedTilbakekrevingUtenOpphør(),
                    attestant = null,
                )
            }
        }

        @Test
        fun `underkjent brev med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())
            val utførtAv = NavIdentBruker.Saksbehandler("utførtAv")
            val fritekst = "underkjentBrevMedTilbakekrevingFritekst"
            innvilgetTilbakekrevingTilAttestering(clock = clock).let { (sak, revurdering) ->
                sak to revurdering.underkjenn(
                    attestering = attesteringUnderkjent(clock),
                    oppgaveId = OppgaveId("underkjentAttesteringOppgave"),
                )
            }.let { (_, revurdering) ->
                revurdering.lagForhåndsvarsel(
                    utførtAv = utførtAv,
                    fritekst = fritekst,
                ).getOrFail() shouldBe expectedForhåndsvarselTilbakekrevingDokumentCommand(
                    revurdering = revurdering,
                    utførtAv = utførtAv,
                    fritekst = fritekst,
                    tilbakekreving = expectedTilbakekrevingUtenOpphør(),
                )
                revurdering.lagDokumentKommando(
                    satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock = clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering,
                    tilbakekreving = expectedTilbakekrevingUtenOpphør(),
                    attestant = null,
                )
            }
        }

        @Test
        fun `iverksatt brev med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())
            val attestantSomIverksatte = NavIdentBruker.Attestant(navIdent = "attestantSomIverksatte")
            innvilgetTilbakekrevingTilAttestering(
                clock = clock,
            ).second.tilIverksatt(
                attestant = attestantSomIverksatte,
                clock = clock,
            ).getOrFail().let { revurdering ->
                revurdering.lagDokumentKommando(
                    satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock = clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering = revurdering,
                    attestant = attestantSomIverksatte,
                    tilbakekreving = expectedTilbakekrevingUtenOpphør(),
                )
            }
        }

        private fun simulertInnvilgetTilbakekreving(
            clock: Clock = TikkendeKlokke(1.august(2021).fixedClock()),
        ): Pair<Sak, SimulertRevurdering.Innvilget> {
            return simulertRevurdering(
                grunnlagsdataOverrides = listOf(
                    fradragsgrunnlagArbeidsinntekt(periode = år(2021), arbeidsinntekt = 5000.0),
                ),
                clock = clock,
                utbetalingerKjørtTilOgMed = { 1.juli(2021) },
            ).let { (sak, revurdering) ->
                requireType<Pair<Sak, SimulertRevurdering.Innvilget>>(sak to revurdering)
            }
        }

        private fun innvilgetTilbakekrevingTilAttestering(
            clock: Clock = TikkendeKlokke(1.august(2021).fixedClock()),
        ): Pair<Sak, RevurderingTilAttestering.Innvilget> {
            return simulertInnvilgetTilbakekreving(
                clock = clock,
            ).let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomSendteTilAttestering"),
                ).getOrFail()
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, RevurderingTilAttestering.Innvilget>>(sak to revurdering)
            }
        }

        private fun expectedTilbakekrevingUtenOpphør() = Tilbakekreving(
            månedBeløp = listOf(
                MånedBeløp(januar(2021), Beløp(5000)),
                MånedBeløp(februar(2021), Beløp(5000)),
                MånedBeløp(mars(2021), Beløp(5000)),
                MånedBeløp(april(2021), Beløp(5000)),
                MånedBeløp(mai(2021), Beløp(6043)),
                MånedBeløp(juni(2021), Beløp(6043)),
            ),
        )
    }

    @Nested
    inner class RevurderingMedOpphør {
        @Test
        fun `simulert revurdering brev med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())
            val utførtAv = NavIdentBruker.Saksbehandler("utførtAv")
            val fritekst = "simulertRevurderingForhåndsvarselMedTilbakekrevingFritekst"
            simulertOpphørTilbakekreving(clock = clock).let { (_, revurdering) ->
                val expectedTilbakekreving = expectedTilbakekrevingForOpphør()
                revurdering.lagForhåndsvarsel(
                    fritekst = fritekst,
                    utførtAv = utførtAv,
                ).getOrFail() shouldBe expectedForhåndsvarselTilbakekrevingDokumentCommand(
                    revurdering = revurdering,
                    utførtAv = utførtAv,
                    fritekst = fritekst,
                    bruttoTilbakekreving = 127762,
                    tilbakekreving = expectedTilbakekreving,
                )
                revurdering.lagDokumentKommando(
                    satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock = clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering = revurdering,
                    tilbakekreving = expectedTilbakekreving,
                    attestant = null,
                )
            }
        }

        @Test
        fun `til attestering brev med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())
            simulertOpphørTilbakekreving(
                clock = clock,
            ).let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomSendteTilAttestering"),
                ).getOrFail()
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, RevurderingTilAttestering.Opphørt>>(sak to revurdering)
                revurdering.lagDokumentKommando(
                    satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock = clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering = revurdering,
                    tilbakekreving = expectedTilbakekrevingForOpphør(),
                    attestant = null,
                )
            }
        }

        @Test
        fun `underkjent brev med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())
            simulertOpphørTilbakekreving(clock = clock).let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomSendteTilAttestering"),
                ).getOrFail().underkjenn(
                    attestering = attesteringUnderkjent(clock),
                    oppgaveId = OppgaveId("underkjentAttesteringOppgave"),
                )
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, UnderkjentRevurdering.Opphørt>>(sak to revurdering)
                val expectedTilbakekreving = expectedTilbakekrevingForOpphør()
                val utførtAv = NavIdentBruker.Saksbehandler("utførtAv")
                val fritekst = "underkjentBrevMedTilbakekrevingFritekst"
                revurdering.lagForhåndsvarsel(
                    fritekst = fritekst,
                    utførtAv = utførtAv,
                ).getOrFail() shouldBe expectedForhåndsvarselTilbakekrevingDokumentCommand(
                    revurdering = revurdering,
                    utførtAv = utførtAv,
                    fritekst = fritekst,
                    bruttoTilbakekreving = 127762,
                    tilbakekreving = expectedTilbakekreving,
                )
                revurdering.lagDokumentKommando(
                    satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock = clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering = revurdering,
                    tilbakekreving = expectedTilbakekreving,
                    attestant = null,
                )
            }
        }

        @Test
        fun `iverksatt brev med tilbakekreving`() {
            val clock = TikkendeKlokke(1.august(2021).fixedClock())
            val attestantSomIverksatte = NavIdentBruker.Attestant(navIdent = "attestantSomIverksatte")
            simulertOpphørTilbakekreving(clock = clock).let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomSendteTilAttestering"),
                ).getOrFail().tilIverksatt(
                    attestant = attestantSomIverksatte,
                    clock = clock,
                ).getOrFail()
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, IverksattRevurdering.Opphørt>>(sak to revurdering)
                revurdering.lagDokumentKommando(
                    satsFactory = satsFactoryTestPåDato(påDato = LocalDate.now(clock)),
                    clock = clock,
                ) shouldBe expectedTilbakekrevingAvPenger(
                    revurdering = revurdering,
                    tilbakekreving = expectedTilbakekrevingForOpphør(),
                    attestant = attestantSomIverksatte,
                )
            }
        }

        private fun simulertOpphørTilbakekreving(
            clock: Clock = TikkendeKlokke(1.august(2021).fixedClock()),
        ): Pair<Sak, SimulertRevurdering.Opphørt> = simulertRevurdering(
            vilkårOverrides = listOf(
                formuevilkårAvslåttPgaBrukersformue(
                    periode = år(2021),
                    bosituasjon = bosituasjongrunnlagEnslig(
                        periode = år(2021),
                    ),
                ),
            ),
            clock = clock,
            utbetalingerKjørtTilOgMed = { 1.juli(2021) },
        ).let { (sak, revurdering) ->
            requireType<Pair<Sak, SimulertRevurdering.Opphørt>>(sak to revurdering)
        }

        private fun expectedTilbakekrevingForOpphør() = Tilbakekreving(
            månedBeløp = listOf(
                MånedBeløp(januar(2021), Beløp(20946)),
                MånedBeløp(februar(2021), Beløp(20946)),
                MånedBeløp(mars(2021), Beløp(20946)),
                MånedBeløp(april(2021), Beløp(20946)),
                MånedBeløp(mai(2021), Beløp(21989)),
                MånedBeløp(juni(2021), Beløp(21989)),
            ),
        )
    }
}
