package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import behandling.domain.AvslagGrunnetBeregning
import behandling.domain.VurderAvslagGrunnetBeregning
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.uføreVilkår
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.Vurdering
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.krevAlleVilkårInnvilget
import vilkår.vurderinger.domain.krevMinstEttAvslag
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

sealed interface IverksattSøknadsbehandling : Søknadsbehandling, KanGenerereBrev {
    abstract override val id: SøknadsbehandlingId
    abstract override val opprettet: Tidspunkt
    abstract override val sakId: UUID
    abstract override val saksnummer: Saksnummer
    abstract override val søknad: Søknad.Journalført.MedOppgave
    abstract override val oppgaveId: OppgaveId
    abstract override val fnr: Fnr
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val aldersvurdering: Aldersvurdering

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt) = KunneIkkeLeggeTilSkattegrunnlag.UgyldigTilstand.left()
    override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling =
        throw IllegalStateException("Skal ikke kunne oppdatere oppgave for en iverksatt søknadsbehandling $id")

    data class Innvilget(
        override val id: SøknadsbehandlingId,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
        override val sakstype: Sakstype,
    ) : IverksattSøknadsbehandling, KanGenerereInnvilgelsesbrev {
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        /**
         * @return null dersom man kaller denne for en alderssak.
         * @throws IllegalStateException Dersom søknadsbehandlingen mangler uføregrunnlag. Dette skal ikke skje. Initen skal også verifisere dette.
         *
         * Se også tilsvarende implementasjon for revurdering: [no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering.Innvilget.hentUføregrunnlag]
         */
        fun hentUføregrunnlag(): NonEmptyList<Uføregrunnlag>? {
            return when (this.sakstype) {
                Sakstype.ALDER -> null

                Sakstype.UFØRE -> {
                    this.vilkårsvurderinger.uføreVilkår()
                        .getOrElse { throw IllegalStateException("Søknadsbehandling uføre: ${this.id} mangler uføregrunnlag") }
                        .grunnlag
                        .toNonEmptyList()
                }
            }
        }
    }

    sealed interface Avslag : IverksattSøknadsbehandling, ErAvslag, KanGenerereAvslagsbrev {
        val erSøknadÅpen: Boolean get() = søknad !is Søknad.Journalført.MedOppgave.Lukket

        /**
         * Lager en ny behandling, og kopierer over data. Behandlingen vil være i tilsvarende tilstand som originalen, før attestering & iverksetting
         * Saksbehandler får da muligheten til å endre behandlingen slik som dem ønsker
         *
         * Merk at opplysningsplikt vilkåret ved [Avslag.UtenBeregning] blir satt til innvilget.
         */
        fun opprettNySøknadsbehandling(
            nyOppgaveId: OppgaveId,
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock,
        ): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling>

        data class MedBeregning(
            override val id: SøknadsbehandlingId,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            override val sakstype: Sakstype,
        ) : Avslag {
            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            override val simulering: Simulering? = null

            init {
                grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> =
                vilkårsvurderinger.avslagsgrunner + avslagsgrunnForBeregning

            override fun opprettNySøknadsbehandling(
                nyOppgaveId: OppgaveId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                clock: Clock,
            ): Either<KunneIkkeOppretteSøknadsbehandling, BeregnetSøknadsbehandling> {
                // TODO - må sjekke stønadsperioden ikke overlapper. Dette blir stoppet ved iverksetting, men dem kan få tilbakemelding mye tidligere
                return erSøknadÅpen.whenever(
                    isFalse = { KunneIkkeOppretteSøknadsbehandling.ErLukket.left() },
                    isTrue = {
                        val opprettet = Tidspunkt.now(clock)
                        BeregnetSøknadsbehandling.Avslag(
                            id = SøknadsbehandlingId.generer(),
                            opprettet = opprettet,
                            sakId = sakId,
                            saksnummer = saksnummer,
                            søknad = søknad,
                            oppgaveId = nyOppgaveId,
                            fnr = fnr,
                            beregning = beregning,
                            fritekstTilBrev = fritekstTilBrev,
                            aldersvurdering = aldersvurdering,
                            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.copyWithNewIds(),
                            attesteringer = Attesteringshistorikk.empty(),
                            søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
                                Søknadsbehandlingshendelse(
                                    tidspunkt = opprettet,
                                    saksbehandler = saksbehandler,
                                    handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(this.id),
                                ),
                            ),
                            sakstype = sakstype,
                            saksbehandler = saksbehandler,
                        ).right()
                    },
                )
            }
        }

        data class UtenBeregning(
            override val id: SøknadsbehandlingId,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            override val sakstype: Sakstype,
        ) : Avslag {
            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            override val beregning = null
            override val simulering: Simulering? = null

            init {
                grunnlagsdataOgVilkårsvurderinger.krevMinstEttAvslag()
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = vilkårsvurderinger.avslagsgrunner

            override fun opprettNySøknadsbehandling(
                nyOppgaveId: OppgaveId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                clock: Clock,
            ): Either<KunneIkkeOppretteSøknadsbehandling, VilkårsvurdertSøknadsbehandling> {
                // TODO - må sjekke stønadsperioden ikke overlapper. Dette blir stoppet ved iverksetting, men dem kan få tilbakemelding mye tidligere
                return erSøknadÅpen.whenever(
                    isFalse = { KunneIkkeOppretteSøknadsbehandling.ErLukket.left() },
                    isTrue = {
                        val opprettet = Tidspunkt.now(clock)
                        val erAvslagGrunnetOpplysningsplikt = vilkårsvurderinger.opplysningsplikt.erAvslag

                        val nyHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
                            Søknadsbehandlingshendelse(
                                tidspunkt = opprettet,
                                saksbehandler = saksbehandler,
                                handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(this.id),
                            ),
                        )

                        val grunnlagsdataOgVilkårsvurderinger = erAvslagGrunnetOpplysningsplikt.whenever(
                            isFalse = {
                                grunnlagsdataOgVilkårsvurderinger.copyWithNewIds()
                            },
                            isTrue = {
                                grunnlagsdataOgVilkårsvurderinger.copyWithNewIds().let {
                                    // siden vi har avslag grunnet opplysningsplikt, så skal vi sette opplysningsplikt til innvilget
                                    // fordi saksbehandlerne ikke manuelt kan endre dette
                                    it.oppdaterOpplysningsplikt(
                                        OpplysningspliktVilkår.Vurdert.tryCreate(
                                            nonEmptyListOf(
                                                VurderingsperiodeOpplysningsplikt.create(
                                                    id = UUID.randomUUID(),
                                                    opprettet = opprettet,
                                                    periode = periode,
                                                    grunnlag = Opplysningspliktgrunnlag(
                                                        id = UUID.randomUUID(),
                                                        opprettet = opprettet,
                                                        periode = periode,
                                                        beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                                                    ),
                                                ),
                                            ),
                                        ).getOrElse { throw IllegalStateException(it.toString()) },
                                    )
                                }
                            },
                        )

                        when (grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.resultat()) {
                            Vurdering.Avslag -> VilkårsvurdertSøknadsbehandling.Avslag(
                                opprettet = opprettet,
                                oppgaveId = nyOppgaveId,
                                saksbehandler = saksbehandler,
                                iverksattSøknadsbehandling = this,
                                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                            )

                            Vurdering.Innvilget -> VilkårsvurdertSøknadsbehandling.Innvilget(
                                id = SøknadsbehandlingId.generer(),
                                opprettet = opprettet,
                                sakId = sakId,
                                saksnummer = saksnummer,
                                søknad = søknad,
                                oppgaveId = nyOppgaveId,
                                fnr = fnr,
                                fritekstTilBrev = fritekstTilBrev,
                                aldersvurdering = aldersvurdering,
                                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                                attesteringer = Attesteringshistorikk.empty(),
                                søknadsbehandlingsHistorikk = nyHistorikk,
                                sakstype = sakstype,
                                saksbehandler = saksbehandler,
                            )

                            Vurdering.Uavklart -> VilkårsvurdertSøknadsbehandling.Uavklart(
                                id = SøknadsbehandlingId.generer(),
                                opprettet = opprettet,
                                sakId = sakId,
                                saksnummer = saksnummer,
                                søknad = søknad,
                                oppgaveId = nyOppgaveId,
                                fnr = fnr,
                                fritekstTilBrev = fritekstTilBrev,
                                aldersvurdering = aldersvurdering,
                                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                                attesteringer = Attesteringshistorikk.empty(),
                                søknadsbehandlingsHistorikk = nyHistorikk,
                                sakstype = sakstype,
                                saksbehandler = saksbehandler,
                            )
                        }.right()
                    },
                )
            }
        }
    }
}

fun VedtakSomKanRevurderes.Companion.fromSøknadsbehandlingInnvilget(
    søknadsbehandling: IverksattSøknadsbehandling.Innvilget,
    utbetalingId: UUID30,
    clock: Clock,
) = VedtakInnvilgetSøknadsbehandling.fromSøknadsbehandling(
    søknadsbehandling = søknadsbehandling,
    utbetalingId = utbetalingId,
    clock = clock,
)
