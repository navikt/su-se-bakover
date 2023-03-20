package no.nav.su.se.bakover.database.revurdering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.ikkeSendBrev
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.revurderingUnderkjent
import no.nav.su.se.bakover.test.sendBrev
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import org.junit.jupiter.api.Test

internal class RevurderingPostgresRepoTest {

    @Test
    fun `saksbehandler som sender til attestering overskriver saksbehandlere som var før`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).also { tdh ->
                val (sak, _, vedtak) = tdh.persisterSøknadsbehandlingIverksatt()
                simulertRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakInnvilgetSøknadsbehandling,
                    saksbehandler = Saksbehandler("kjella"),
                ).also { (_, revurdering) ->
                    revurdering.shouldBeType<SimulertRevurdering.Innvilget>().also { simulert ->
                        tdh.revurderingRepo.lagre(simulert)
                        tdh.revurderingRepo.hent(simulert.id)!!.shouldBeType<SimulertRevurdering.Innvilget>().also {
                            it.saksbehandler shouldBe Saksbehandler("kjella")
                            it.oppgaveId shouldBe oppgaveIdRevurdering
                        }

                        tdh.revurderingRepo.lagre(
                            simulert.tilAttestering(
                                attesteringsoppgaveId = OppgaveId("attesteringsoppgave id"),
                                saksbehandler = Saksbehandler("arve"),
                            ).getOrFail(),
                        )
                        tdh.revurderingRepo.hent(simulert.id)!!.shouldBeType<RevurderingTilAttestering.Innvilget>().also {
                            it.oppgaveId shouldBe OppgaveId("attesteringsoppgave id")
                            it.saksbehandler shouldBe Saksbehandler("arve")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `bevarer verdier for id og opprettet`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).also { tdh ->
                val (sak, _, vedtak) = tdh.persisterSøknadsbehandlingIverksatt()

                val opprettet = opprettetRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakEndringIYtelse,
                ).second.copy(id = revurderingId)
                tdh.revurderingRepo.lagre(opprettet).also {
                    tdh.revurderingRepo.hent(revurderingId) shouldBe opprettet.also {
                        it.avkorting shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående
                    }
                }

                // lagrer noe nytt på samme id
                iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                ).second.shouldBeType<IverksattRevurdering.Innvilget>().copy(id = opprettet.id).also { iverksattt ->
                    iverksattt.id shouldBe opprettet.id
                    iverksattt.opprettet shouldNotBe opprettet.opprettet

                    tdh.revurderingRepo.lagre(iverksattt)
                    tdh.revurderingRepo.hent(opprettet.id)!!.shouldBeType<IverksattRevurdering.Innvilget>().also {
                        it.id shouldBe opprettet.id
                        it.opprettet shouldBe opprettet.opprettet
                    }
                }
            }
        }
    }

    @Test
    fun `lagrer og henter alle tilstander av revurdering`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).also { tdh ->
                val (sak, _, vedtak) = tdh.persisterSøknadsbehandlingIverksatt()

                val opprettet = opprettetRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakEndringIYtelse,
                    clock = tdh.clock,
                ).second.copy(id = revurderingId)
                tdh.revurderingRepo.lagre(opprettet).also {
                    tdh.revurderingRepo.hent(revurderingId) shouldBe opprettet.also {
                        it.avkorting shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående
                    }
                }

                val beregnetInnvilget = beregnetRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                ).second.shouldBeType<BeregnetRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(beregnetInnvilget).also {
                    tdh.revurderingRepo.hent(beregnetInnvilget.id) shouldBe beregnetInnvilget.also {
                        it.avkorting shouldBe AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
                    }
                }

                val beregnetOpphørt = beregnetRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    clock = tdh.clock,
                ).second.shouldBeType<BeregnetRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(beregnetOpphørt).also {
                    tdh.revurderingRepo.hent(beregnetOpphørt.id) shouldBe beregnetOpphørt.also { revurdering ->
                        revurdering.avkorting shouldBe AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
                    }
                }

                val simulertInnvilget = simulertRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<SimulertRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(simulertInnvilget).also {
                    tdh.revurderingRepo.hent(simulertInnvilget.id) shouldBe simulertInnvilget.also {
                        it.avkorting.shouldBeType<AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående>()
                    }
                }

                val simulertOpphørt = simulertRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = 30.april(2021),
                    clock = tdh.clock,
                ).second.shouldBeType<SimulertRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(simulertOpphørt).also {
                    tdh.revurderingRepo.hent(simulertOpphørt.id) shouldBe simulertOpphørt.also {
                        it.avkorting.shouldBeType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>().also { revurdering ->
                            tdh.avkortingsvarselRepo.hent(revurdering.avkortingsvarsel.id) shouldBe null
                        }
                    }
                }

                val tilAttesteringInnvilget = revurderingTilAttestering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<RevurderingTilAttestering.Innvilget>()
                tdh.revurderingRepo.lagre(tilAttesteringInnvilget).also {
                    tdh.revurderingRepo.hent(tilAttesteringInnvilget.id) shouldBe tilAttesteringInnvilget.also {
                        it.avkorting.shouldBeType<AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående>()
                    }
                }

                val tilAttesteringOpphørt = revurderingTilAttestering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = 30.april(2021),
                    clock = tdh.clock,
                ).second.shouldBeType<RevurderingTilAttestering.Opphørt>()
                tdh.revurderingRepo.lagre(tilAttesteringOpphørt).also {
                    tdh.revurderingRepo.hent(tilAttesteringOpphørt.id) shouldBe tilAttesteringOpphørt.also { revurdering ->
                        revurdering.avkorting.shouldBeType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>().also {
                            tdh.avkortingsvarselRepo.hent(it.avkortingsvarsel.id) shouldBe null
                        }
                    }
                }

                val underkjentInnvilget = revurderingUnderkjent(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<UnderkjentRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(underkjentInnvilget).also {
                    tdh.revurderingRepo.hent(underkjentInnvilget.id) shouldBe underkjentInnvilget.also {
                        it.avkorting.shouldBeType<AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående>()
                    }
                }

                val underkjentOpphørt = revurderingUnderkjent(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = 30.april(2021),
                    clock = tdh.clock,
                ).second.shouldBeType<UnderkjentRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(underkjentOpphørt).also {
                    tdh.revurderingRepo.hent(underkjentOpphørt.id) shouldBe underkjentOpphørt.also { revurdering ->
                        revurdering.avkorting.shouldBeType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>().also {
                            tdh.avkortingsvarselRepo.hent(it.avkortingsvarsel.id) shouldBe null
                        }
                    }
                }

                val iverksattInnvilget = iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<IverksattRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(iverksattInnvilget).also {
                    tdh.revurderingRepo.hent(iverksattInnvilget.id) shouldBe iverksattInnvilget.also {
                        it.avkorting.shouldBeType<AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående>()
                    }
                }

                val iverksattOpphørt = iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = 30.april(2021),
                    clock = tdh.clock,
                ).second.shouldBeType<IverksattRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(iverksattOpphørt).also {
                    tdh.revurderingRepo.hent(iverksattOpphørt.id) shouldBe iverksattOpphørt.also { revurdering ->
                        revurdering.avkorting.shouldBeType<AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel>().also {
                            tdh.avkortingsvarselRepo.hent(it.avkortingsvarsel.id)!!.shouldBeType<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `hent og lagre brevvalg`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).also { helper ->
                helper.persisterSimulertRevurdering().second.shouldBeType<SimulertRevurdering.Innvilget>().also {
                    helper.revurderingRepo.lagre(it.copy(brevvalgRevurdering = BrevvalgRevurdering.IkkeValgt))
                    helper.revurderingRepo.hent(it.id)!!.brevvalgRevurdering shouldBe BrevvalgRevurdering.IkkeValgt

                    helper.revurderingRepo.lagre(
                        it.leggTilBrevvalg(
                            sendBrev(
                                fritekst = "fri tekset",
                                begrunnelse = "beggy",
                                bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
                            ),
                        ).getOrFail(),
                    )
                    helper.revurderingRepo.hent(it.id)!!.brevvalgRevurdering shouldBe BrevvalgRevurdering.Valgt.SendBrev(
                        fritekst = "fri tekset",
                        begrunnelse = "beggy",
                        bestemtAv = BrevvalgRevurdering.BestemtAv.Systembruker,
                    )

                    helper.revurderingRepo.lagre(
                        it.leggTilBrevvalg(
                            ikkeSendBrev(
                                begrunnelse = "vil ikke",
                                bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler("kjella"),
                            ),
                        ).getOrFail(),
                    )
                    helper.revurderingRepo.hent(it.id)!!.brevvalgRevurdering shouldBe BrevvalgRevurdering.Valgt.IkkeSendBrev(
                        begrunnelse = "vil ikke",
                        bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler("kjella"),
                    )
                }
            }
        }
    }
}
