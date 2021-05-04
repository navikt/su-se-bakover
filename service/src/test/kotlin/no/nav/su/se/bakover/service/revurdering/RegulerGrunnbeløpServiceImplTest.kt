package no.nav.su.se.bakover.service.revurdering

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periode
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsakRegulerGrunnbeløp
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingVedtak
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class RegulerGrunnbeløpServiceImplTest {

    @Test
    fun `kaster exception hvis man forsøker å legge til flere uføregrunnlag`() {
        val revurderingMock = mock<OpprettetRevurdering>()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingMock
        }

        assertThrows<IllegalArgumentException> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
            ).leggTilUføregrunnlag(
                revurderingId = revurderingId,
                uføregrunnlag = listOf(mock(), mock(), mock()),
            )
        }.also {
            it.message shouldContain "Flere perioder med forskjellig IEU støttes ikke enda"
        }
    }

    @Test
    fun `oppdaterer behandlingsinformasjon når uføregrunnlag legges til`() {
        val eksisterendeGrunnlagsdata = Grunnlagsdata(
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    uføregrad = Uføregrad.parse(20),
                    forventetInntekt = 10,
                ),
            ),
        )

        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            forhåndsvarsel = null,
            grunnlagsdata = eksisterendeGrunnlagsdata,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet(
                    vurderingsperioder = listOf(
                        Vurderingsperiode.Manuell(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = eksisterendeGrunnlagsdata.uføregrunnlag.first(),
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        )

        val nyttUføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            uføregrad = Uføregrad.parse(45),
            forventetInntekt = 20,
        )

        val forventetLagretRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = opprettetRevurdering.behandlingsinformasjon.copy(
                uførhet = opprettetRevurdering.behandlingsinformasjon.uførhet!!.copy(
                    forventetInntekt = nyttUføregrunnlag.forventetInntekt,
                    uføregrad = nyttUføregrunnlag.uføregrad.value,
                ),
            ),
            forhåndsvarsel = null,
            grunnlagsdata = opprettetRevurdering.grunnlagsdata,
            vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturnConsecutively listOf(
                opprettetRevurdering,
                forventetLagretRevurdering.copy(grunnlagsdata = Grunnlagsdata(uføregrunnlag = listOf(nyttUføregrunnlag))),
            )
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        val grunnlagServiceMock = mock<GrunnlagService>() {
            on { simulerEndretGrunnlagsdata(any(), any(), any()) } doReturn GrunnlagService.SimulerEndretGrunnlagsdata(
                førBehandling = opprettetRevurdering.grunnlagsdata,
                endring = Grunnlagsdata(uføregrunnlag = listOf(nyttUføregrunnlag)),
                resultat = Grunnlagsdata(uføregrunnlag = listOf(nyttUføregrunnlag)),
            )
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            grunnlagService = grunnlagServiceMock,
        ).leggTilUføregrunnlag(
            revurderingId = revurderingId,
            uføregrunnlag = listOf(nyttUføregrunnlag),
        )

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(revurderingRepoMock).lagre(argThat { it shouldBe forventetLagretRevurdering })
            verify(vilkårsvurderingServiceMock).lagre(
                argThat { it shouldBe opprettetRevurdering.id },
                any(),
            )
            verify(revurderingRepoMock).hent(forventetLagretRevurdering.id)
            verify(grunnlagServiceMock).simulerEndretGrunnlagsdata(
                sakId = forventetLagretRevurdering.sakId,
                periode = forventetLagretRevurdering.periode,
                endring = Grunnlagsdata(uføregrunnlag = listOf(nyttUføregrunnlag)),
            )
        }
        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `G-regulering med uendret fradrag og forventetInntekt fører til IngenEndring`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt().copy(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                uføregrad = 20,
                forventetInntekt = 12000,
                begrunnelse = "forrigeBegrunnelse",
            ),
        )
        val fradrag = object : Fradrag {
            override fun getFradragstype() = Fradragstype.ForventetInntekt
            override fun getMånedsbeløp() = 1000.0
            override fun getUtenlandskInntekt(): UtenlandskInntekt? = null
            override fun getTilhører() = FradragTilhører.BRUKER
            override val periode = Periode.create(1.januar(2020), 31.januar(2020))
            override fun equals(other: Any?) =
                throw IllegalStateException("Skal ikke kalles fra testen")
        }
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = søknadsbehandlingVedtak.copy(
                beregning = object : Beregning {
                    private val id = UUID.randomUUID()
                    private val tidspunkt = fixedTidspunkt.minus(1, ChronoUnit.DAYS)
                    override fun getId() = id
                    override fun getOpprettet() = tidspunkt
                    override fun getSats() = Sats.HØY
                    override fun getMånedsberegninger() = listOf(
                        object : Månedsberegning {
                            override fun getSumYtelse() = 19637
                            override fun getSumFradrag() = 1000.0
                            override fun getBenyttetGrunnbeløp() = 99858
                            override fun getSats() = Sats.HØY
                            override fun getSatsbeløp() = 20637.32
                            override fun getFradrag() = listOf(fradrag)
                            override fun getFribeløpForEps(): Double? = null

                            override val periode = Periode.create(1.januar(2020), 31.januar(2020))
                            override fun equals(other: Any?) =
                                throw IllegalStateException("Skal ikke kalles fra testen")
                        },
                    )

                    override fun getFradrag() = listOf(fradrag)
                    override fun getSumYtelse() = 19637
                    override fun getSumFradrag() = 12000.0
                    override val periode = periode
                    override fun getFradragStrategyName() = FradragStrategyName.Enslig
                    override fun getBegrunnelse() = "forrigeBegrunnelse"
                    override fun equals(other: Any?) = throw IllegalStateException("Skal ikke kalles fra testen")
                },
                behandlingsinformasjon = behandlingsinformasjon,
            ),
            saksbehandler = BehandlingTestUtils.saksbehandler,
            oppgaveId = BehandlingTestUtils.søknadOppgaveId,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak.copy(årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP),
            behandlingsinformasjon = behandlingsinformasjon,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = periode,
                        uføregrad = Uføregrad.parse(20),
                        forventetInntekt = 12000,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )
        val expectedBeregnetRevurdering = BeregnetRevurdering.IngenEndring(
            id = opprettetRevurdering.id,
            periode = opprettetRevurdering.periode,
            opprettet = opprettetRevurdering.opprettet,
            tilRevurdering = opprettetRevurdering.tilRevurdering,
            saksbehandler = opprettetRevurdering.saksbehandler,
            oppgaveId = opprettetRevurdering.oppgaveId,
            fritekstTilBrev = opprettetRevurdering.fritekstTilBrev,
            revurderingsårsak = opprettetRevurdering.revurderingsårsak,
            behandlingsinformasjon = opprettetRevurdering.behandlingsinformasjon,
            beregning = opprettetRevurdering.tilRevurdering.beregning,
            forhåndsvarsel = null,
            grunnlagsdata = opprettetRevurdering.grunnlagsdata,
            vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = BehandlingTestUtils.saksbehandler,
            fradrag = emptyList(),
        ).orNull()!! as BeregnetRevurdering.IngenEndring

        actual shouldBe expectedBeregnetRevurdering

        inOrder(
            revurderingRepoMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
        )
    }

    @Test
    fun `Ikke lov å sende en Simulert Opphørt til attestering`() {
        val simulertRevurdering = SimulertRevurdering.Opphørt(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
            simulering = mock(),
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ) shouldBeLeft KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `Ikke lov å sende en Underkjent Opphørt til attestering`() {
        val simulertRevurdering = UnderkjentRevurdering.Opphørt(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
            simulering = mock(),
            attestering = mock(),
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ) shouldBeLeft KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `En attestert beregnet revurdering skal ikke sende brev`() {
        val beregnetRevurdering = BeregnetRevurdering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn beregnetRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).orNull()!! as RevurderingTilAttestering.IngenEndring

        actual shouldBe RevurderingTilAttestering.IngenEndring(
            tilRevurdering = søknadsbehandlingVedtak,
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "Fritekst",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = actual.beregning,
            skalFøreTilBrevutsending = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())

            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `En attestert underkjent revurdering skal ikke sende brev`() {
        val underkjentRevurdering = UnderkjentRevurdering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
            attestering = mock(),
            skalFøreTilBrevutsending = true,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn underkjentRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).orNull()!! as RevurderingTilAttestering.IngenEndring

        actual shouldBe RevurderingTilAttestering.IngenEndring(
            tilRevurdering = søknadsbehandlingVedtak,
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "Fritekst",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = actual.beregning,
            skalFøreTilBrevutsending = false,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())

            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `iverksetter endring av ytelse`() {
        val attestant = NavIdentBruker.Attestant("attestant")

        val iverksattRevurdering = IverksattRevurdering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            skalFøreTilBrevutsending = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            skalFøreTilBrevutsending = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }

        val vedtakRepoMock = mock<VedtakRepo>()
        val eventObserver: EventObserver = mock()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vedtakRepo = vedtakRepoMock,
        ).apply { addObserver(eventObserver) }
            .iverksett(
                revurderingId = revurderingTilAttestering.id,
                attestant = attestant,
            ) shouldBe iverksattRevurdering.right()

        inOrder(
            revurderingRepoMock,
            vedtakRepoMock,
            eventObserver,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(vedtakRepoMock).lagre(
                argThat {
                    it should beOfType<Vedtak.IngenEndringIYtelse>()
                    it.vedtakType shouldBe VedtakType.INGEN_ENDRING
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe iverksattRevurdering })
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering)
                },
            )
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            vedtakRepoMock,
        )
    }
}
