package no.nav.su.se.bakover.database

import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo.ForhåndsvarselDto
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class RevurderingPostgresRepoTest {
    private val ds = EmbeddedDatabase.instance()
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(ds)
    private val repo: RevurderingPostgresRepo = RevurderingPostgresRepo(ds, søknadsbehandlingRepo)
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val saksbehandler = Saksbehandler("Sak S. Behandler")
    private val periode = Periode.create(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.desember(2020),
    )
    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )
    private val oppgaveId = OppgaveId("oppgaveid")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val simulering = Simulering(
        gjelderId = FnrGenerator.random(),
        gjelderNavn = "et navn for simulering",
        datoBeregnet = 1.januar(2021),
        nettoBeløp = 200,
        periodeList = listOf(),
    )

    private fun opprettet(vedtak: Vedtak.EndringIYtelse) = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = periode,
        opprettet = fixedTidspunkt,
        tilRevurdering = vedtak,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        behandlingsinformasjon = vedtak.behandlingsinformasjon,
    )

    private fun beregnetIngenEndring(
        opprettet: OpprettetRevurdering,
        vedtak: Vedtak.EndringIYtelse,
    ) = BeregnetRevurdering.IngenEndring(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak,
        saksbehandler = opprettet.saksbehandler,
        oppgaveId = opprettet.oppgaveId,
        fritekstTilBrev = opprettet.fritekstTilBrev,
        revurderingsårsak = opprettet.revurderingsårsak,
        beregning = vedtak.beregning,
        forhåndsvarsel = null,
        behandlingsinformasjon = vedtak.behandlingsinformasjon,
    )

    private fun beregnetInnvilget(
        opprettet: OpprettetRevurdering,
        vedtak: Vedtak.EndringIYtelse,
    ) = BeregnetRevurdering.Innvilget(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak,
        saksbehandler = opprettet.saksbehandler,
        beregning = vedtak.beregning,
        oppgaveId = opprettet.oppgaveId,
        fritekstTilBrev = opprettet.fritekstTilBrev,
        revurderingsårsak = opprettet.revurderingsårsak,
        forhåndsvarsel = null,
        behandlingsinformasjon = vedtak.behandlingsinformasjon,
    )

    private fun beregnetOpphørt(
        opprettet: OpprettetRevurdering,
        vedtak: Vedtak.EndringIYtelse,
    ) = BeregnetRevurdering.Opphørt(
        id = opprettet.id,
        periode = opprettet.periode,
        opprettet = opprettet.opprettet,
        tilRevurdering = vedtak,
        saksbehandler = opprettet.saksbehandler,
        beregning = vedtak.beregning,
        oppgaveId = opprettet.oppgaveId,
        fritekstTilBrev = opprettet.fritekstTilBrev,
        revurderingsårsak = opprettet.revurderingsårsak,
        forhåndsvarsel = null,
        behandlingsinformasjon = vedtak.behandlingsinformasjon,
    )

    private fun simulertInnvilget(beregnet: BeregnetRevurdering.Innvilget) = SimulertRevurdering.Innvilget(
        id = beregnet.id,
        periode = beregnet.periode,
        opprettet = beregnet.opprettet,
        tilRevurdering = beregnet.tilRevurdering,
        saksbehandler = beregnet.saksbehandler,
        beregning = beregnet.beregning,
        oppgaveId = beregnet.oppgaveId,
        simulering = simulering,
        fritekstTilBrev = beregnet.fritekstTilBrev,
        revurderingsårsak = beregnet.revurderingsårsak,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        behandlingsinformasjon = beregnet.behandlingsinformasjon,
    )

    private fun simulertOpphørt(beregnet: BeregnetRevurdering.Opphørt) = SimulertRevurdering.Opphørt(
        id = beregnet.id,
        periode = beregnet.periode,
        opprettet = beregnet.opprettet,
        tilRevurdering = beregnet.tilRevurdering,
        saksbehandler = beregnet.saksbehandler,
        beregning = beregnet.beregning,
        oppgaveId = beregnet.oppgaveId,
        simulering = simulering,
        fritekstTilBrev = beregnet.fritekstTilBrev,
        revurderingsårsak = beregnet.revurderingsårsak,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        behandlingsinformasjon = beregnet.behandlingsinformasjon,
    )

    @Test
    fun `kan opprette og beregner med ingen endring`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            repo.hent(opprettet.id) shouldBe opprettet

            val beregnetIngenEndring = beregnetIngenEndring(opprettet, vedtak)

            repo.lagre(beregnetIngenEndring)
            repo.hent(opprettet.id) shouldBe beregnetIngenEndring
        }
    }

    @Test
    fun `kan beregne (innvilget) og oppdatere periode og årsak`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettetRevurdering = opprettet(vedtak)
            repo.lagre(opprettetRevurdering)
            val innvilgetBeregning = beregnetInnvilget(opprettetRevurdering, vedtak)

            repo.lagre(innvilgetBeregning)
            repo.hent(innvilgetBeregning.id) shouldBe innvilgetBeregning

            val oppdatertRevurdering = innvilgetBeregning.oppdater(
                Periode.create(1.juni(2020), 30.juni(2020)),
                Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("begrunnelse"),
                ),
            )

            repo.lagre(oppdatertRevurdering)
            repo.hent(innvilgetBeregning.id) shouldBe oppdatertRevurdering
        }
    }

    @Test
    fun `beregnet ingen endring kan overskrives med ny saksbehandler`() {

        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetIngenEndring(opprettet, vedtak)

            repo.lagre(beregnet)

            val nyBeregnet = beregnet.copy(
                saksbehandler = Saksbehandler("ny saksbehandler"),
            )

            repo.lagre(nyBeregnet)

            val actual = repo.hent(opprettet.id)!!

            actual shouldNotBe opprettet
            actual shouldNotBe beregnet
            actual shouldBe nyBeregnet
        }
    }

    @Test
    fun `kan overskrive en beregnet med simulert`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            repo.hent(opprettet.id) shouldBe simulert
        }
    }

    @Test
    fun `kan overskrive en simulert med en beregnet`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet.copy(forhåndsvarsel = simulert.forhåndsvarsel)
        }
    }

    @Test
    fun `kan overskrive en simulert med en til attestering`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fritekst",
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                )

            repo.lagre(tilAttestering)

            repo.hent(opprettet.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `saksbehandler som sender til attestering overskriver saksbehandlere som var før`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            val tilAttestering = simulert.tilAttestering(
                attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                saksbehandler = Saksbehandler("Ny saksbehandler"),
                fritekstTilBrev = "fritekst",
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            )

            repo.lagre(tilAttestering)

            repo.hent(opprettet.id) shouldBe tilAttestering

            tilAttestering.saksbehandler shouldNotBe opprettet.saksbehandler
        }
    }

    @Test
    fun `kan lagre og hente en iverksatt revurdering`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            repo.oppdaterForhåndsvarsel(opprettet.id, Forhåndsvarsel.IngenForhåndsvarsel)

            val tilAttestering = RevurderingTilAttestering.Innvilget(
                id = opprettet.id,
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = vedtak.beregning,
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "Navn Navnesson",
                    datoBeregnet = LocalDate.now(),
                    nettoBeløp = 5,
                    periodeList = listOf(),
                ),
                oppgaveId = oppgaveId,
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                behandlingsinformasjon = vedtak.behandlingsinformasjon,
            )

            repo.lagre(tilAttestering)

            val utbetaling = testDataHelper.nyUtbetalingUtenKvittering(
                revurderingTilAttestering = tilAttestering,
            )

            val iverksatt = tilAttestering.tilIverksatt(
                attestant = attestant,
                utbetal = { utbetaling.id.right() },
            ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

            repo.lagre(iverksatt)
            repo.hent(iverksatt.id) shouldBe iverksatt
            ds.withSession {
                repo.hentRevurderingerForSak(iverksatt.sakId, it) shouldBe listOf(iverksatt)
            }
        }
    }

    @Test
    fun `kan lagre og hente en underkjent revurdering`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = opprettet(vedtak)

            repo.lagre(opprettet)

            val beregnet = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnet)

            val simulert = simulertInnvilget(beregnet)

            repo.lagre(simulert)

            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "",
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                )
            repo.lagre(tilAttestering)

            val attestering = Attestering.Underkjent(
                attestant = attestant,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "feil",
            )

            val underkjent = tilAttestering.underkjenn(attestering, OppgaveId("nyOppgaveId"))
            repo.lagre(underkjent)

            repo.hent(opprettet.id) shouldBe underkjent
            repo.hentEventuellTidligereAttestering(opprettet.id) shouldBe attestering
        }
    }

    @Test
    fun `beregnet, simulert og underkjent opphørt`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet
            val simulert = simulertOpphørt(beregnet)
            repo.lagre(simulert)
            repo.hent(opprettet.id) shouldBe simulert
            val tilAttestering =
                simulert.tilAttestering(
                    opprettet.oppgaveId,
                    opprettet.saksbehandler,
                    Forhåndsvarsel.IngenForhåndsvarsel,
                    opprettet.fritekstTilBrev,
                ).orNull()!!
            repo.lagre(tilAttestering)

            val underkjent = UnderkjentRevurdering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                attestering = Attestering.Underkjent(
                    attestant,
                    Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    "kommentar",
                ),
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id)!! shouldBe underkjent
        }
    }

    @Test
    fun `til attestering opphørt`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.lagre(simulertOpphørt(beregnet))
            val underkjent = RevurderingTilAttestering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id)!! shouldBe underkjent
        }
    }

    @Test
    fun `iverksatt opphørt`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetOpphørt(opprettet, vedtak)
            repo.lagre(beregnet)
            val simulert = simulertOpphørt(beregnet)
            repo.lagre(simulert)
            val tilAttestering =
                simulert.tilAttestering(
                    opprettet.oppgaveId,
                    opprettet.saksbehandler,
                    Forhåndsvarsel.IngenForhåndsvarsel,
                    opprettet.fritekstTilBrev,
                ).orNull()!!
            repo.lagre(tilAttestering)

            val underkjent = IverksattRevurdering.Opphørt(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                simulering = simulering,
                attestering = Attestering.Iverksatt(
                    attestant,
                ),
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id)!! shouldBe underkjent
        }
    }

    @Test
    fun `beregnet, simulert og underkjent ingen endring`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            repo.hent(opprettet.id) shouldBe beregnet
            val underkjentTilAttestering = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )
            repo.lagre(underkjentTilAttestering)
            val underkjent = UnderkjentRevurdering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                attestering = Attestering.Underkjent(
                    attestant,
                    Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    "kommentar",
                ),
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id)!! shouldBe underkjent
        }
    }

    @Test
    fun `til attestering ingen endring`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            val underkjent = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                skalFøreTilBrevutsending = true,
                forhåndsvarsel = null,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id)!! shouldBe underkjent
        }
    }

    @Test
    fun `iverksatt ingen endring`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)
            val beregnet = beregnetIngenEndring(opprettet, vedtak)
            repo.lagre(beregnet)
            val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )
            repo.lagre(revurderingTilAttestering)
            val underkjent = IverksattRevurdering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                oppgaveId = opprettet.oppgaveId,
                fritekstTilBrev = opprettet.fritekstTilBrev,
                revurderingsårsak = opprettet.revurderingsårsak,
                beregning = vedtak.beregning,
                attestering = Attestering.Iverksatt(
                    attestant,
                ),
                skalFøreTilBrevutsending = false,
                forhåndsvarsel = null,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
            )

            repo.lagre(underkjent)
            repo.hent(underkjent.id)!! shouldBe underkjent
        }
    }

    @Test
    fun `Lagrer revurdering med ingen forhåndsvarsel`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            repo.oppdaterForhåndsvarsel(opprettet.id, Forhåndsvarsel.IngenForhåndsvarsel)

            repo.hent(opprettet.id)!!.forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel
        }
    }

    @Test
    fun `Lagrer revurdering med sendt forhåndsvarsel`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            val forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt(
                journalpostId = JournalpostId("journalpostId"),
                brevbestillingId = BrevbestillingId("brevbestillignsId"),
            )

            repo.oppdaterForhåndsvarsel(
                opprettet.id,
                forhåndsvarsel,
            )
            repo.hent(opprettet.id)!!.forhåndsvarsel shouldBe forhåndsvarsel
        }
    }

    @Test
    fun `Lagrer revurdering med besluttet forhåndsvarsel`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet)

            val forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                journalpostId = JournalpostId("journalpostId"),
                brevbestillingId = BrevbestillingId("brevbestillignsId"),
                valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                begrunnelse = "",
            )

            repo.oppdaterForhåndsvarsel(
                opprettet.id,
                forhåndsvarsel,
            )
            repo.hent(opprettet.id)!!.forhåndsvarsel shouldBe forhåndsvarsel
        }
    }

    @Test
    fun `Bare opprettet og simulerte revurderinger kan lagre forhåndsvarsel`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = opprettet(vedtak)
            repo.lagre(opprettet.copy(forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel))

            repo.hent(opprettet.id)!!.forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel

            val beregnetRevurdering = beregnetInnvilget(opprettet, vedtak)

            repo.lagre(beregnetRevurdering)
            repo.hent(beregnetRevurdering.id)!!.forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel

            val simulertRevurdering = simulertInnvilget(beregnetRevurdering)
            repo.lagre(simulertRevurdering)
            repo.hent(beregnetRevurdering.id)!!.forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel

            val nyOpprettet = opprettet(vedtak)
            repo.lagre(nyOpprettet.copy(forhåndsvarsel = null))

            repo.hent(nyOpprettet.id)!!.forhåndsvarsel shouldBe null

            val nyBeregnetRevurdering = beregnetInnvilget(nyOpprettet, vedtak)

            repo.lagre(nyBeregnetRevurdering)
            repo.hent(nyBeregnetRevurdering.id)!!.forhåndsvarsel shouldBe null

            val nySimulertRevurdering = simulertInnvilget(nyBeregnetRevurdering.copy(forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel))
            repo.lagre(nySimulertRevurdering)
            repo.hent(nySimulertRevurdering.id)!!.forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel
        }
    }

    @Test
    fun `ingen frhåndsvarsel json`() {
        //language=JSON
        val ingenJson = """
            {
              "type": "IngenForhåndsvarsel"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            ingenJson,
            serialize(ForhåndsvarselDto.from(Forhåndsvarsel.IngenForhåndsvarsel)),
            true,
        )
    }

    @Test
    fun `sendt frhåndsvarsel json`() {
        //language=JSON
        val sendtJson = """
            {
              "type": "Sendt",
              "journalpostId": "1",
              "brevbestillingId": "2"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            sendtJson,
            serialize(
                ForhåndsvarselDto.from(
                    Forhåndsvarsel.SkalForhåndsvarsles.Sendt(
                        JournalpostId("1"),
                        BrevbestillingId("2"),
                    ),
                ),
            ),
            true,
        )
    }

    @Test
    fun `besluttet frhåndsvarsel json`() {
        //language=JSON
        val besluttetJson = """
            {
              "type": "Besluttet",
              "journalpostId": "1",
              "brevbestillingId": "2",
              "valg": "FortsettSammeOpplysninger",
              "begrunnelse": "begrunnelse"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            besluttetJson,
            serialize(
                ForhåndsvarselDto.from(
                    Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                        JournalpostId("1"),
                        BrevbestillingId("2"),
                        BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                        "begrunnelse"
                    ),
                ),
            ),
            true,
        )
    }
}
