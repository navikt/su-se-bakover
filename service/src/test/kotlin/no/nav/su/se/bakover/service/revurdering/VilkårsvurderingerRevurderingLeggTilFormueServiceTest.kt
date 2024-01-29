package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.steg.Vurderingstatus
import no.nav.su.se.bakover.domain.revurdering.vilkår.formue.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgaBrukersformue
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.Vurdering
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.Verdier
import vilkår.vurderinger.domain.Konsistensproblem
import java.util.UUID

internal class VilkårsvurderingerRevurderingLeggTilFormueServiceTest {

    @Test
    fun `legg til revurdering av formue happy case`() {
        val nyFormue = LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
            periode = år(2021),
            epsFormue = null,
            søkersFormue = Verdier.empty(),
            begrunnelse = null,
        )
        val (sak, opprettet) = opprettetRevurdering(
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Formue)),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettet
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = listOf(nyFormue).toNonEmptyList(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            ).getOrFail()

            actual.shouldBeType<RevurderingOgFeilmeldingerResponse>().let { response ->
                response.revurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(
                    mapOf(Revurderingsteg.Formue to Vurderingstatus.Vurdert),
                )
                response.revurdering.vilkårsvurderinger.formue.shouldBeType<FormueVilkår.Vurdert>().let {
                    it.vurderingsperioder shouldHaveSize 1
                    it.vurderingsperioder.single().periode shouldBe nyFormue.periode
                    it.vurdering shouldBe Vurdering.Innvilget
                    it.grunnlag shouldHaveSize 1
                    it.grunnlag.single().periode shouldBe nyFormue.periode
                    it.grunnlag.single().epsFormue shouldBe nyFormue.epsFormue
                    it.grunnlag.single().søkersFormue shouldBe nyFormue.søkersFormue
                }
            }
            verify(serviceAndMocks.revurderingRepo).hent(revurderingId)
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(
                argThat { it shouldBe actual.revurdering },
                anyOrNull(),
            )
        }
    }

    @Test
    fun `skal ikke være lov å legge inn formue for eps, hvis man ikke har noen eps`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    grunnlagsdataOverrides = listOf(
                        Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = år(2021),
                        ),
                    ),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = listOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = år(2021),
                            epsFormue = Verdier.empty(),
                            søkersFormue = Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ).toNonEmptyList(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            ).getOrElse {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.Konsistenssjekk(Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig)
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `ikke lov å legge inn formue periode utenfor perioden til revurderingen`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    revurderingsperiode = år(2021),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = listOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = år(2020),
                            epsFormue = null,
                            søkersFormue = Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ).toNonEmptyList(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            ).getOrElse {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden)
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `ikke lov å legge inn epsformue periode utenfor perioden til revurderingen`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    revurderingsperiode = år(2021),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = listOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = Periode.create(1.januar(2021), 31.mars(2021)),
                            epsFormue = null,
                            søkersFormue = Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ).toNonEmptyList(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            ).getOrElse {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.Konsistenssjekk(
                    Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode,
                )
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `ikke lov å legge inn formue med overlappende perioder`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    grunnlagsdataOverrides = listOf(
                        Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                            id = UUID.randomUUID(),
                            fnr = epsFnr,
                            opprettet = fixedTidspunkt,
                            periode = år(2021),
                        ),
                    ),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = listOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = periodeJanMars2021,
                            epsFormue = Verdier.empty(),
                            søkersFormue = Verdier.empty(),
                            begrunnelse = null,
                        ),
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = periodeMarsDesember2021,
                            epsFormue = Verdier.empty(),
                            søkersFormue = Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ).toNonEmptyList(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            ).getOrElse {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder)
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `feilmelding hvis vi ikke finner revurdering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = listOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = år(2021),
                            epsFormue = Verdier.empty(),
                            søkersFormue = Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ).toNonEmptyList(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            ).getOrElse {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering
            }
        }
    }

    @Test
    fun `når formue blir avslått, og uførhet er det også, får vi feil om at utfallet ikke støttes pga opphør av flere vilkår`() {
        val (sak, opprettet) = opprettetRevurdering(
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                listOf(
                    Revurderingsteg.Uførhet,
                    Revurderingsteg.Formue,
                ),
            ),
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    periode = år(2021),
                ),
                formuevilkårAvslåttPgaBrukersformue(),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettet
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = opprettet.id,
                    formuegrunnlag = nonEmptyListOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = år(2021),
                            epsFormue = null,
                            søkersFormue = Verdier.create(
                                verdiIkkePrimærbolig = 10000000,
                                verdiEiendommer = 0,
                                verdiKjøretøy = 0,
                                innskudd = 0,
                                verdipapir = 0,
                                pengerSkyldt = 0,
                                kontanter = 0,
                                depositumskonto = 0,
                            ),
                            begrunnelse = ":(",
                        ),
                    ),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            ).getOrFail()

            response.feilmeldinger.shouldContain(RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår)

            verify(it.revurderingRepo).hent(opprettet.id)
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(
                argThat { it shouldBe response.revurdering },
                anyOrNull(),
            )
            verify(it.sakService).hentSakForRevurdering(opprettet.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får feilmelding om at opphør ikke er fra første måned i revurderingsperioden`() {
        val (sak, opprettet) = opprettetRevurdering()
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettet
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = opprettet.id,
                    formuegrunnlag = nonEmptyListOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = Periode.create(1.januar(2021), 31.mai(2021)),
                            epsFormue = null,
                            søkersFormue = Verdier.empty(),
                            begrunnelse = ":)",
                        ),
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = Periode.create(1.juni(2021), 31.desember(2021)),
                            epsFormue = null,
                            søkersFormue = Verdier.create(
                                verdiIkkePrimærbolig = 10000000,
                                verdiEiendommer = 0,
                                verdiKjøretøy = 0,
                                innskudd = 0,
                                verdipapir = 0,
                                pengerSkyldt = 0,
                                kontanter = 0,
                                depositumskonto = 0,
                            ),
                            begrunnelse = ":(",
                        ),
                    ),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),

            ).getOrFail()

            response.feilmeldinger.shouldContain(RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned)
        }
    }

    @Test
    fun `kan ikke legge inn formue når revurdering er til attestering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering().second
            },
        ).let {
            val response = it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = listOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = år(2021),
                            epsFormue = null,
                            søkersFormue = Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ).toNonEmptyList(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            )

            response shouldBe KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(
                fra = RevurderingTilAttestering.Innvilget::class,
                til = OpprettetRevurdering::class,
            ).left()
        }
    }

    private val periodeJanMars2021 = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mars(2021))
    private val periodeMarsDesember2021 = Periode.create(fraOgMed = 1.mars(2021), tilOgMed = 31.desember(2021))
}
