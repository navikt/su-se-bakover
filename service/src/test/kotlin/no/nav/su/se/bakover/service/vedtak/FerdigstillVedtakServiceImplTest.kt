package no.nav.su.se.bakover.service.vedtak

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest.AvslagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.attestantNavn
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.brevbestillingIdVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.journalført
import no.nav.su.se.bakover.test.journalførtOgDistribuertBrev
import no.nav.su.se.bakover.test.journalpostIdVedtak
import no.nav.su.se.bakover.test.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksbehandlerNavn
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.util.UUID

internal class FerdigstillVedtakServiceImplTest {

    @Test
    fun `prøver ikke ferdigstille dersom kvittering er feil`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                oversendtUtbetalingMedKvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
                ),
            )
        }
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er gjennoppta`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                oversendtUtbetalingMedKvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    type = Utbetaling.UtbetalingsType.GJENOPPTA,
                ),
            )
        }
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er stans`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                oversendtUtbetalingMedKvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    type = Utbetaling.UtbetalingsType.STANS,
                ),
            )
        }
    }

    @Test
    fun `ferdigstill NY kaster feil hvis utbetalinga ikke kan kobles til et vedtak`() {

        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn null
            },
        ) {
            assertThrows<FerdigstillVedtakServiceImpl.KunneIkkeFerdigstilleVedtakException> {
                service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)
            }.message shouldContain vedtak.utbetalingId.toString()

            verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
        }
    }

    @Test
    fun `ferdigstill NY kaster feil hvis man ikke finner person for journalpost`() {

        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ) {
            assertThrows<FerdigstillVedtakServiceImpl.KunneIkkeFerdigstilleVedtakException> {
                service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)
            }.message shouldContain vedtak.id.toString()

            verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
            verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
        }
    }

    @Test
    fun `ferdigstillelse etter utbetaling kaster feil generering av brev feiler`() {
        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()
        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn attestantNavn.right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
            },
        ) {
            assertThrows<FerdigstillVedtakServiceImpl.KunneIkkeFerdigstilleVedtakException> {
                service.ferdigstillVedtakEtterUtbetaling(
                    sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering,
                )
            }.message shouldContain vedtak.id.toString()

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(argThat { it shouldBe vedtak.utbetalingId })
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
                verify(brevService).lagBrev(argThat { it shouldBe instanceOf<LagBrevRequest.InnvilgetVedtak>() })
            }
        }
    }

    @Test
    fun `ferdigstill NY etter utbetaling går fint`() {
        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()
        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn attestantNavn.right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn "brev".toByteArray().right()
            },
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
        ) {
            service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(argThat { it shouldBe vedtak.utbetalingId })
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
                verify(brevService).lagBrev(argThat { it shouldBe instanceOf<LagBrevRequest.InnvilgetVedtak>() })
                verify(brevService).lagreDokument(
                    argThat {
                        it.generertDokument contentEquals "brev".toByteArray()
                        it.metadata shouldBe Dokument.Metadata(
                            sakId = sak.id,
                            vedtakId = vedtak.id,
                            bestillBrev = true,
                        )
                    },
                )
                verify(oppgaveService).lukkOppgaveMedSystembruker(argThat { it shouldBe vedtak.behandling.oppgaveId })
                verify(behandlingMetrics).incrementInnvilgetCounter(argThat { it shouldBe BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE })
            }
        }
    }

    @Test
    fun `ferdigstill NY av regulering av grunnbeløp etter utbetaling skal ikke sende brev men skal lukke oppgave`() {

        val (sak, vedtak) = vedtakRevurderingIverksattInnvilget(
            sakOgVedtakSomKanRevurderes = innvilgetSøknadsbehandlingVedtak(),
            revurderingsårsak = Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                Revurderingsårsak.Begrunnelse.create("Regulering av grunnbeløpet påvirket ytelsen."),
            ),
        )

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
        ) {
            service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger[1] as Utbetaling.OversendtUtbetaling.MedKvittering)

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
                verify(oppgaveService).lukkOppgaveMedSystembruker(vedtak.behandling.oppgaveId)
            }
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner saksbehandler for journalpost`() {

        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            },
        ) {

            service.journalførOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant.left()

            inOrder(
                *all(),
            ) {
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
                verify(microsoftGraphApiClient).hentNavnForNavIdent(argThat { it shouldBe vedtak.saksbehandler })
            }
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner person for journalpost`() {

        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ) {
            service.journalførOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkePerson.left()

            inOrder(personService) {
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            }
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner attestant for journalpost`() {

        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            },
        ) {
            service.journalførOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant.left()

            inOrder(
                *all(),
            ) {
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
            }
        }
    }

    @Test
    fun `sender ikke brev for revurdering ingen endring som ikke skal føre til brevutsending`() {
        val vedtak = innvilgetSøknadsbehandlingVedtak().second.let {
            it.copy(
                behandling = IverksattRevurdering.IngenEndring(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    oppgaveId = it.behandling.oppgaveId,
                    beregning = beregning(),
                    saksbehandler = saksbehandler,
                    attesteringer = Attesteringshistorikk.empty()
                        .leggTilNyAttestering(Attestering.Iverksatt(attestant, Tidspunkt.now())),
                    fritekstTilBrev = "",
                    periode = it.periode,
                    tilRevurdering = it,
                    revurderingsårsak = Revurderingsårsak(
                        Revurderingsårsak.Årsak.ANDRE_KILDER,
                        Revurderingsårsak.Begrunnelse.create("begrunnelse"),
                    ),
                    skalFøreTilBrevutsending = false,
                    forhåndsvarsel = null,
                    grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                    vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
                    informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                ),
            )
        }
        FerdigstillVedtakServiceMocks {
            service.journalførOgLagre(vedtak) shouldBe vedtak.right()
        }
    }

    @Test
    fun `svarer med feil dersom journalføring av brev feiler`() {

        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn attestantNavn.right()
            },
            brevService = mock {
                on {
                    journalførBrev(
                        any(),
                        any(),
                    )
                } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
            },
        ) {
            service.journalførOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FeilVedJournalføring.left()

            inOrder(
                *all(),
            ) {
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
                verify(brevService).journalførBrev(
                    request = argThat {
                        it shouldBe AvslagBrevRequest(
                            person = person(),
                            saksbehandlerNavn = saksbehandlerNavn,
                            attestantNavn = attestantNavn,
                            fritekst = "",
                            forventetInntektStørreEnn0 = false,
                            avslag = Avslag(
                                Tidspunkt.now(fixedClock),
                                avslagsgrunner = vedtak.avslagsgrunner,
                                harEktefelle = false,
                                beregning = vedtak.beregning,
                            ),
                        )
                    },
                    saksnummer = argThat { it shouldBe vedtak.behandling.saksnummer },
                )
            }
        }
    }

    @Test
    fun `svarer med feil dersom journalføring av brev allerede er utført`() {

        val vedtak = journalførtAvslagsVedtak()

        FerdigstillVedtakServiceMocks(
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn attestantNavn.right()
            },
        ) {
            service.journalførOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.AlleredeJournalført(
                journalpostIdVedtak,
            ).left()

            inOrder(
                *all(),
            ) {
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
            }
        }
    }

    @Test
    fun `oppdaterer vedtak og lagrer dersom journalføring går fint`() {

        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn attestantNavn.right()
            },
            brevService = mock {
                on { journalførBrev(any(), any()) } doReturn journalpostIdVedtak.right()
            },
        ) {
            service.journalførOgLagre(vedtak) shouldBe vedtak.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                    journalpostIdVedtak,
                ),
            ).right()

            inOrder(
                *all(),
            ) {
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
                verify(brevService).journalførBrev(
                    argThat {
                        it shouldBe AvslagBrevRequest(
                            person = person(),
                            avslag = Avslag(
                                Tidspunkt.now(fixedClock),
                                avslagsgrunner = vedtak.avslagsgrunner,
                                harEktefelle = false,
                                beregning = vedtak.beregning,
                            ),
                            saksbehandlerNavn = saksbehandlerNavn,
                            attestantNavn = attestantNavn,
                            fritekst = "",
                            forventetInntektStørreEnn0 = false,
                        )
                    },
                    argThat { it shouldBe vedtak.behandling.saksnummer },
                )
                verify(vedtakRepo).lagre(
                    vedtak.copy(
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                            journalpostIdVedtak,
                        ),
                    ),
                )
                verify(behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
            }
        }
    }

    @Test
    fun `svarer med feil dersom brevdistribusjon feiler`() {

        val vedtak = journalførtAvslagsVedtak()

        FerdigstillVedtakServiceMocks(
            brevService = mock {
                on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
            },
        ) {
            service.distribuerOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.FeilVedDistribusjon(
                journalpostIdVedtak,
            ).left()

            inOrder(
                *all(),
            ) {
                verify(brevService).distribuerBrev(journalpostIdVedtak)
            }
        }
    }

    @Test
    fun `svarer med feil dersom distribusjon ikke er journalført først`() {

        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks {
            service.distribuerOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
        }
    }

    @Test
    fun `svarer med feil dersom distribusjon av brev allerede er utført`() {

        val vedtak = journalførtOgDistribuertAvslagsVedtak()

        FerdigstillVedtakServiceMocks {
            service.distribuerOgLagre(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.AlleredeDistribuert(
                journalpostIdVedtak,
            ).left()
        }
    }

    @Test
    fun `oppdaterer vedtak og lagrer dersom brevdistribusjon går fint`() {

        val vedtak = journalførtAvslagsVedtak()

        FerdigstillVedtakServiceMocks(
            brevService = mock {
                on { distribuerBrev(any()) } doReturn brevbestillingIdVedtak.right()
            },
        ) {
            service.distribuerOgLagre(vedtak) shouldBe vedtak.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    journalpostIdVedtak,
                    brevbestillingIdVedtak,
                ),
            ).right()

            inOrder(
                *all(),
            ) {
                verify(brevService).distribuerBrev(journalpostIdVedtak)
                verify(vedtakRepo).lagre(
                    vedtak.copy(
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                            journalpostIdVedtak,
                            brevbestillingIdVedtak,
                        ),
                    ),
                )
                verify(behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
            }
        }
    }

    @Test
    fun `svarer med feil dersom lukking av oppgave feiler`() {

        val vedtak = journalførtOgDistribuertAvslagsVedtak()

        FerdigstillVedtakServiceMocks(
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
            },
        ) {
            service.lukkOppgaveMedBruker(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave.left()

            inOrder(
                *all(),
            ) {
                verify(oppgaveService).lukkOppgave(vedtak.behandling.oppgaveId)
            }
        }
    }

    @Test
    fun `opprettelse av manglende journalpost og brevbestilling gjør ingenting hvis ingenting mangler`() {

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentUtenJournalpost() } doReturn emptyList()
                on { hentUtenBrevbestilling() } doReturn emptyList()
            },
        ) {
            service.opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = emptyList(),
                brevbestillingsresultat = emptyList(),
            )

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentUtenJournalpost()
                verify(vedtakRepo).hentUtenBrevbestilling()
            }
        }
    }

    @Test
    fun `opprettelse av manglende journalpost feiler teknisk`() {

        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentUtenJournalpost() } doReturn listOf(vedtak)
                on { hentUtenBrevbestilling() } doReturn emptyList()
            },
            brevService = mock {
                on {
                    journalførBrev(
                        any(),
                        any(),
                    )
                } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn attestantNavn.right()
            },
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
        ) {
            service.opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = listOf(
                    FerdigstillVedtakService.KunneIkkeOppretteJournalpostForIverksetting(
                        sakId = vedtak.behandling.sakId,
                        behandlingId = vedtak.behandling.id,
                        grunn = "FeilVedJournalføring",
                    ).left(),
                ),
                brevbestillingsresultat = emptyList(),
            )
            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentUtenJournalpost()
                verify(personService).hentPersonMedSystembruker(vedtak.behandling.fnr)
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
                verify(brevService).journalførBrev(
                    argThat {
                        it shouldBe AvslagBrevRequest(
                            person = person(),
                            avslag = Avslag(
                                Tidspunkt.now(fixedClock),
                                avslagsgrunner = vedtak.avslagsgrunner,
                                harEktefelle = false,
                                beregning = vedtak.beregning,
                            ),
                            saksbehandlerNavn = saksbehandlerNavn,
                            attestantNavn = attestantNavn,
                            fritekst = "",
                            forventetInntektStørreEnn0 = false,
                        )
                    },
                    argThat { it shouldBe vedtak.behandling.saksnummer },
                )
                verify(vedtakRepo).hentUtenBrevbestilling()
                verify(oppgaveService).lukkOppgaveMedSystembruker(argThat { it shouldBe vedtak.behandling.oppgaveId })
                verify(behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
            }
        }
    }

    @Test
    fun `oppretter manglende journalpost for vedtak`() {

        val avslagsVedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentUtenJournalpost() } doReturn listOf(avslagsVedtak)
                on { hentUtenBrevbestilling() } doReturn emptyList()
            },
            brevService = mock {
                on { journalførBrev(any(), any()) } doReturn journalpostIdVedtak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person().right()
            },
            microsoftGraphApiClient = mock {
                on { hentNavnForNavIdent(saksbehandler) } doReturn saksbehandlerNavn.right()
                on { hentNavnForNavIdent(attestant) } doReturn attestantNavn.right()
            },
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
        ) {
            service.opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = listOf(
                    FerdigstillVedtakService.OpprettetJournalpostForIverksetting(
                        sakId = avslagsVedtak.behandling.sakId,
                        behandlingId = avslagsVedtak.behandling.id,
                        journalpostId = journalpostIdVedtak,
                    ).right(),
                ),
                brevbestillingsresultat = emptyList(),
            )

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentUtenJournalpost()
                verify(personService).hentPersonMedSystembruker(argThat { it shouldBe avslagsVedtak.behandling.fnr })
                inOrder(microsoftGraphApiClient) {
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(saksbehandler)
                    verify(microsoftGraphApiClient).hentNavnForNavIdent(attestant)
                }
                verify(brevService).journalførBrev(
                    request = argThat {
                        it shouldBe AvslagBrevRequest(
                            person = person(),
                            saksbehandlerNavn = saksbehandlerNavn,
                            attestantNavn = attestantNavn,
                            fritekst = "",
                            forventetInntektStørreEnn0 = false,
                            avslag = Avslag(
                                Tidspunkt.now(fixedClock),
                                avslagsgrunner = avslagsVedtak.avslagsgrunner,
                                harEktefelle = false,
                                beregning = avslagsVedtak.beregning,
                            ),
                        )
                    },
                    saksnummer = argThat { it shouldBe avslagsVedtak.behandling.saksnummer },
                )
                verify(vedtakRepo).lagre(
                    argThat {
                        it shouldBe avslagsVedtak.copy(
                            journalføringOgBrevdistribusjon = journalført,
                        )
                    },
                )
                verify(behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
                verify(vedtakRepo).hentUtenBrevbestilling()
                verify(oppgaveService).lukkOppgaveMedSystembruker(argThat { it shouldBe avslagsVedtak.behandling.oppgaveId })
                verify(behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
            }
        }
    }

    @Test
    fun `oppretter manglende brevbestilling for journalført vedtak`() {
        val (sak, innvilgelseUtenBrevbestilling) = journalførtInnvilgetVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentUtenJournalpost() } doReturn emptyList()
                on { hentUtenBrevbestilling() } doReturn listOf(innvilgelseUtenBrevbestilling)
            },
            utbetalingRepo = mock {
                on { hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId) } doReturn sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling
            },
            brevService = mock {
                on { distribuerBrev(any()) } doReturn brevbestillingIdVedtak.right()
            },
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
        ) {
            service.opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = emptyList(),
                brevbestillingsresultat = listOf(
                    FerdigstillVedtakService.BestiltBrev(
                        sakId = innvilgelseUtenBrevbestilling.behandling.sakId,
                        behandlingId = innvilgelseUtenBrevbestilling.behandling.id,
                        journalpostId = journalpostIdVedtak,
                        brevbestillingId = brevbestillingIdVedtak,
                    ).right(),
                ),
            )

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentUtenJournalpost()
                verify(vedtakRepo).hentUtenBrevbestilling()
                verify(utbetalingRepo).hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId)
                verify(brevService).distribuerBrev(journalpostIdVedtak)
                verify(vedtakRepo).lagre(
                    argThat {
                        it shouldBe innvilgelseUtenBrevbestilling.copy(
                            journalføringOgBrevdistribusjon = journalførtOgDistribuertBrev,
                        )
                    },
                )
                verify(behandlingMetrics).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                verify(oppgaveService).lukkOppgaveMedSystembruker(argThat { it shouldBe innvilgelseUtenBrevbestilling.behandling.oppgaveId })
                verify(behandlingMetrics).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
        }
    }

    @Test
    fun `kan ikke opprette manglende brevbestilling hvis vedtak ikke er journalført`() {

        val (sak, innvilgelseUtenBrevbestilling) = innvilgetSøknadsbehandlingVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentUtenJournalpost() } doReturn emptyList()
                on { hentUtenBrevbestilling() } doReturn listOf(innvilgelseUtenBrevbestilling)
            },
            utbetalingRepo = mock {
                on { hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId) } doReturn sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling
            },
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
        ) {
            service.opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = emptyList(),
                brevbestillingsresultat = listOf(
                    FerdigstillVedtakService.KunneIkkeBestilleBrev(
                        sakId = innvilgelseUtenBrevbestilling.behandling.sakId,
                        behandlingId = innvilgelseUtenBrevbestilling.behandling.id,
                        journalpostId = null,
                        grunn = "MåJournalføresFørst",
                    ).left(),
                ),
            )

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentUtenJournalpost()
                verify(vedtakRepo).hentUtenBrevbestilling()
                verify(utbetalingRepo).hentUtbetaling(innvilgelseUtenBrevbestilling.utbetalingId)
                verify(oppgaveService).lukkOppgaveMedSystembruker(argThat { it shouldBe innvilgelseUtenBrevbestilling.behandling.oppgaveId })
                verify(behandlingMetrics).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
        }
    }

    @Test
    fun `oppretter ikke manglende journalpost for vedtak med ukvitterte utbetalinger eller kvitteringer med feil`() {
        val innvilgetVedtakUkvittertUtbetaling = innvilgetSøknadsbehandlingVedtak().second
        val ukvittertUtbetaling = mock<Utbetaling.OversendtUtbetaling.UtenKvittering>()

        val innvilgetVedtakKvitteringMedFeil = innvilgetSøknadsbehandlingVedtak().second
        val kvitteringMedFeil = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { kvittering } doReturn Kvittering(Kvittering.Utbetalingsstatus.FEIL, "")
        }

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentUtenJournalpost() } doReturn emptyList()
                on { hentUtenBrevbestilling() } doReturn listOf(
                    innvilgetVedtakUkvittertUtbetaling,
                    innvilgetVedtakKvitteringMedFeil,
                )
            },
            utbetalingRepo = mock {
                on { hentUtbetaling(innvilgetVedtakUkvittertUtbetaling.utbetalingId) } doReturn ukvittertUtbetaling
                on { hentUtbetaling(innvilgetVedtakKvitteringMedFeil.utbetalingId) } doReturn kvitteringMedFeil
            },
        ) {
            service.opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() shouldBe FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = emptyList(),
                brevbestillingsresultat = emptyList(),
            )
            verify(vedtakRepo).hentUtenJournalpost()
            verify(vedtakRepo).hentUtenBrevbestilling()
            verify(utbetalingRepo).hentUtbetaling(innvilgetVedtakUkvittertUtbetaling.utbetalingId)
            verify(utbetalingRepo).hentUtbetaling(innvilgetVedtakKvitteringMedFeil.utbetalingId)
        }
    }

    internal data class FerdigstillVedtakServiceMocks(
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val clock: Clock = fixedClock,
        val microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
        val brevService: BrevService = mock(),
        val utbetalingService: UtbetalingService = mock(),
        val vedtakRepo: VedtakRepo = mock(),
        val utbetalingRepo: UtbetalingRepo = mock(),
        val behandlingMetrics: BehandlingMetrics = mock(),
        val runTest: FerdigstillVedtakServiceMocks.() -> Unit,
    ) {
        val service = FerdigstillVedtakServiceImpl(
            oppgaveService = oppgaveService,
            personService = personService,
            utbetalingService = utbetalingService,
            clock = clock,
            microsoftGraphApiOppslag = microsoftGraphApiClient,
            brevService = brevService,
            vedtakRepo = vedtakRepo,
            utbetalingRepo = utbetalingRepo,
            behandlingMetrics = behandlingMetrics,
        )

        init {
            runTest()
            verifyNoMoreInteractions()
        }

        fun all() = listOf(
            oppgaveService,
            personService,
            microsoftGraphApiClient,
            brevService,
            utbetalingService,
            vedtakRepo,
            utbetalingRepo,
            behandlingMetrics,
        ).toTypedArray()

        private fun verifyNoMoreInteractions() {
            com.nhaarman.mockitokotlin2.verifyNoMoreInteractions(
                *all(),
            )
        }
    }

    private fun avslagsVedtak(): Vedtak.Avslag.AvslagBeregning =
        vedtakSøknadsbehandlingIverksattAvslagMedBeregning().second

    private fun journalførtAvslagsVedtak(): Vedtak.Avslag.AvslagBeregning {
        return avslagsVedtak().copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                journalpostIdVedtak,
            ),
        )
    }

    private fun journalførtOgDistribuertAvslagsVedtak(): Vedtak.Avslag.AvslagBeregning {
        return avslagsVedtak().copy(
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                journalpostIdVedtak,
                brevbestillingIdVedtak,
            ),
        )
    }

    private fun innvilgetSøknadsbehandlingVedtak(): Pair<Sak, Vedtak.EndringIYtelse> {
        return vedtakSøknadsbehandlingIverksattInnvilget()
    }

    private fun journalførtInnvilgetVedtak(): Pair<Sak, Vedtak.EndringIYtelse> {
        return innvilgetSøknadsbehandlingVedtak().let {
            it.copy(
                second = it.second.copy(
                    journalføringOgBrevdistribusjon = journalført,
                ),
            )
        }
    }

    private fun journalførtOgDistribuertInnvilgetVedtak(): Pair<Sak, Vedtak.EndringIYtelse> {
        return innvilgetSøknadsbehandlingVedtak().let {
            it.copy(
                second = it.second.copy(
                    journalføringOgBrevdistribusjon = journalførtOgDistribuertBrev,
                ),
            )
        }
    }
}
