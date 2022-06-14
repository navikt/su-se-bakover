package no.nav.su.se.bakover.domain.brev

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.requireType
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgrBrukersformue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock

class BrevForTilbakekrevingTest {

    private val person = person()

    private fun simulertInnvilgetTilbakekreving(): Pair<Sak, SimulertRevurdering.Innvilget> = simulertRevurdering(
        grunnlagsdataOverrides = listOf(
            fradragsgrunnlagArbeidsinntekt(periode = år(2021), arbeidsinntekt = 5000.0),
        ),
    ).let {
        requireType(it)
    }

    private fun simulertOpphørTilbakekreving(): Pair<Sak, SimulertRevurdering.Opphørt> = simulertRevurdering(
        vilkårOverrides = listOf(
            formuevilkårAvslåttPgrBrukersformue(
                periode = år(2021),
                bosituasjon = bosituasjongrunnlagEnslig(
                    periode = år(2021),
                ),
            ),
        ),
    ).let {
        requireType(it)
    }

    @Nested
    inner class RevurderingUtenOpphør {
        @Test
        fun `simulert revurdering brev med tilbakekreving`() {
            simulertInnvilgetTilbakekreving().let { (sak, revurdering) ->
                requireType<Pair<Sak, SimulertRevurdering.Innvilget>>(sak to revurdering)
                val forhåndsvarsel = requireType<LagBrevRequest.ForhåndsvarselTilbakekreving>(
                    revurdering.lagForhåndsvarsel(
                        person = person,
                        saksbehandlerNavn = "kjell",
                        fritekst = "da",
                        clock = fixedClock,
                    ).getOrFail(),
                )
                requireType<Dokument.UtenMetadata.Informasjon>(
                    forhåndsvarsel.tilDokument { "fakePDF".toByteArray().right() }
                        .getOrFail(),
                )

                requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
            }
        }

        @Test
        fun `til attestering brev med tilbakekreving`() {
            simulertInnvilgetTilbakekreving().let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = oppgaveIdRevurdering,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fri",
                ).getOrFail()
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, RevurderingTilAttestering.Innvilget>>(sak to revurdering)
                requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
            }
        }

        @Test
        fun `underkjent brev med tilbakekreving`() {
            simulertInnvilgetTilbakekreving().let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = oppgaveIdRevurdering,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fri",
                ).getOrFail().underkjenn(
                    attestering = attesteringUnderkjent(fixedClock),
                    oppgaveId = oppgaveIdRevurdering,
                )
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, UnderkjentRevurdering.Innvilget>>(sak to revurdering)
                requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
            }
        }

        @Test
        fun `iverksatt brev med tilbakekreving`() {
            simulertInnvilgetTilbakekreving().let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = oppgaveIdRevurdering,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fri",
                ).getOrFail().tilIverksatt(
                    attestant = attestant,
                    hentOpprinneligAvkorting = { null },
                    clock = fixedClock,
                ).getOrFail()
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, IverksattRevurdering.Innvilget>>(sak to revurdering)
                val brev = requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
                requireType<Dokument.UtenMetadata.Vedtak>(
                    brev.tilDokument { "fakePDF".toByteArray().right() }
                        .getOrFail(),
                )
            }
        }
    }

    @Nested
    inner class RevurderingMedOpphør {
        @Test
        fun `simulert revurdering brev med tilbakekreving`() {
            simulertOpphørTilbakekreving().let { (sak, revurdering) ->
                requireType<Pair<Sak, SimulertRevurdering.Opphørt>>(sak to revurdering)
                val forhåndsvarsel = requireType<LagBrevRequest.ForhåndsvarselTilbakekreving>(
                    revurdering.lagForhåndsvarsel(
                        person = person,
                        saksbehandlerNavn = "kjell",
                        fritekst = "da",
                        clock = fixedClock,
                    ).getOrFail(),
                )
                requireType<Dokument.UtenMetadata.Informasjon>(
                    forhåndsvarsel.tilDokument { "fakePDF".toByteArray().right() }
                        .getOrFail(),
                )

                requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
            }
        }

        @Test
        fun `til attestering brev med tilbakekreving`() {
            simulertOpphørTilbakekreving().let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = oppgaveIdRevurdering,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fri",
                ).getOrFail()
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, RevurderingTilAttestering.Opphørt>>(sak to revurdering)
                requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
            }
        }

        @Test
        fun `underkjent brev med tilbakekreving`() {
            simulertOpphørTilbakekreving().let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = oppgaveIdRevurdering,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fri",
                ).getOrFail().underkjenn(
                    attestering = attesteringUnderkjent(fixedClock),
                    oppgaveId = oppgaveIdRevurdering,
                )
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, UnderkjentRevurdering.Opphørt>>(sak to revurdering)
                requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
            }
        }

        @Test
        fun `iverksatt brev med tilbakekreving`() {
            simulertOpphørTilbakekreving().let { (sak, revurdering) ->
                sak to revurdering.tilAttestering(
                    attesteringsoppgaveId = oppgaveIdRevurdering,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fri",
                ).getOrFail().tilIverksatt(
                    attestant = attestant,
                    hentOpprinneligAvkorting = { null },
                    clock = fixedClock,
                ).getOrFail()
            }.let { (sak, revurdering) ->
                requireType<Pair<Sak, IverksattRevurdering.Opphørt>>(sak to revurdering)
                val brev = requireType<LagBrevRequest.TilbakekrevingAvPenger>(
                    lagVisitor(sak = sak).let { visitor ->
                        revurdering.accept(visitor)
                        visitor.brevRequest.getOrFail()
                    },
                )
                requireType<Dokument.UtenMetadata.Vedtak>(
                    brev.tilDokument { "fakePDF".toByteArray().right() }
                        .getOrFail(),
                )
            }
        }
    }

    private fun lagVisitor(
        hentPerson: (fnr: Fnr) -> Either<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson, Person> = { person.right() },
        hentNavn: (navIdentBruker: NavIdentBruker) -> Either<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String> = { "kjell".right() },
        sak: Sak,
        clock: Clock = fixedClock,
    ): LagBrevRequestVisitor {
        return LagBrevRequestVisitor(
            hentPerson = hentPerson,
            hentNavn = hentNavn,
            hentGjeldendeUtbetaling = { _, forDato ->
                sak.utbetalingstidslinje().gjeldendeForDato(forDato)!!.beløp.right()
            },
            clock = clock,
            satsFactory = satsFactoryTestPåDato(),
        )
    }
}
