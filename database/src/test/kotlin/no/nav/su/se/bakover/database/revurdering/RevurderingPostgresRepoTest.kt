package no.nav.su.se.bakover.database.revurdering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
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
import no.nav.su.se.bakover.test.saksbehandler
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
                    clock = tdh.clock,
                ).also { (_, revurdering) ->
                    revurdering.shouldBeType<SimulertRevurdering.Innvilget>().also { simulert ->
                        tdh.revurderingRepo.lagre(simulert)
                        tdh.revurderingRepo.hent(simulert.id)!!.shouldBeType<SimulertRevurdering.Innvilget>().also {
                            it.saksbehandler shouldBe saksbehandler
                            it.oppgaveId shouldBe oppgaveIdRevurdering
                        }

                        tdh.revurderingRepo.lagre(
                            simulert.tilAttestering(
                                saksbehandler = Saksbehandler("DenAndreSaksbehandleren"),
                            ).getOrFail(),
                        )
                        tdh.revurderingRepo.hent(simulert.id)!!.shouldBeType<RevurderingTilAttestering.Innvilget>().also {
                            it.oppgaveId shouldBe OppgaveId("oppgaveIdRevurdering")
                            it.saksbehandler shouldBe Saksbehandler("DenAndreSaksbehandleren")
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
                    clock = tdh.clock,
                    sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakEndringIYtelse,
                ).second.copy(id = revurderingId)
                tdh.revurderingRepo.lagre(opprettet).also {
                    tdh.revurderingRepo.hent(revurderingId) shouldBe opprettet
                }

                // lagrer noe nytt på samme id
                iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<IverksattRevurdering.Innvilget>().copy(id = opprettet.id).also { iverksatt ->
                    iverksatt.id shouldBe opprettet.id
                    iverksatt.opprettet shouldNotBe opprettet.opprettet

                    tdh.revurderingRepo.lagre(iverksatt)
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
                    tdh.revurderingRepo.hent(revurderingId) shouldBe opprettet
                }

                val beregnetInnvilget = beregnetRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                ).second.shouldBeType<BeregnetRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(beregnetInnvilget).also {
                    tdh.revurderingRepo.hent(beregnetInnvilget.id) shouldBe beregnetInnvilget
                }

                val beregnetOpphørt = beregnetRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    clock = tdh.clock,
                ).second.shouldBeType<BeregnetRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(beregnetOpphørt).also {
                    tdh.revurderingRepo.hent(beregnetOpphørt.id) shouldBe beregnetOpphørt
                }

                val simulertInnvilget = simulertRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<SimulertRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(simulertInnvilget).also {
                    tdh.revurderingRepo.hent(simulertInnvilget.id) shouldBe simulertInnvilget
                }

                val simulertOpphørt = simulertRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = { 30.april(2021) },
                    clock = tdh.clock,
                ).second.shouldBeType<SimulertRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(simulertOpphørt).also {
                    tdh.revurderingRepo.hent(simulertOpphørt.id) shouldBe simulertOpphørt
                }

                val tilAttesteringInnvilget = revurderingTilAttestering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<RevurderingTilAttestering.Innvilget>()
                tdh.revurderingRepo.lagre(tilAttesteringInnvilget)

                val tilAttesteringOpphørt = revurderingTilAttestering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = { 30.april(2021) },
                    clock = tdh.clock,
                ).second.shouldBeType<RevurderingTilAttestering.Opphørt>()
                tdh.revurderingRepo.lagre(tilAttesteringOpphørt).also {
                    tdh.revurderingRepo.hent(tilAttesteringOpphørt.id) shouldBe tilAttesteringOpphørt
                }

                val underkjentInnvilget = revurderingUnderkjent(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<UnderkjentRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(underkjentInnvilget).also {
                    tdh.revurderingRepo.hent(underkjentInnvilget.id) shouldBe underkjentInnvilget
                }

                val underkjentOpphørt = revurderingUnderkjent(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = { 30.april(2021) },
                    clock = tdh.clock,
                ).second.shouldBeType<UnderkjentRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(underkjentOpphørt).also {
                    tdh.revurderingRepo.hent(underkjentOpphørt.id) shouldBe underkjentOpphørt
                }

                val iverksattInnvilget = iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    clock = tdh.clock,
                ).second.shouldBeType<IverksattRevurdering.Innvilget>()
                tdh.revurderingRepo.lagre(iverksattInnvilget).also {
                    tdh.revurderingRepo.hent(iverksattInnvilget.id) shouldBe iverksattInnvilget
                }
                val iverksattOpphørt = iverksattRevurdering(
                    sakOgVedtakSomKanRevurderes = sak to vedtak,
                    vilkårOverrides = listOf(
                        utenlandsoppholdAvslag(),
                    ),
                    utbetalingerKjørtTilOgMed = { 30.april(2021) },
                    clock = tdh.clock,
                ).second.shouldBeType<IverksattRevurdering.Opphørt>()
                tdh.revurderingRepo.lagre(iverksattOpphørt).also {
                    tdh.revurderingRepo.hent(iverksattOpphørt.id) shouldBe iverksattOpphørt
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

                        ),
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
                        ),
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
