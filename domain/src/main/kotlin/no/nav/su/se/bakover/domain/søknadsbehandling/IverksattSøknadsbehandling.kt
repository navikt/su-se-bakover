package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import beregning.domain.Beregning
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
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.uføreVilkår
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.Avslagsgrunn
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.StøtterHentingAvEksternGrunnlag
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
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
        /**
         * Lager en ny behandling, og kopierer over data. Behandlingen vil være i tilsvarende tilstand som originalen, før attestering & iverksetting
         * Saksbehandler får da muligheten til å endre behandlingen slik som dem ønsker
         *
         * Merk at opplysningsplikt vilkåret ved [Avslag.UtenBeregning] blir satt til innvilget.
         */
        fun opprettNySøknadsbehandling(
            kanOppretteNyBehandling: Boolean,
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
                kanOppretteNyBehandling: Boolean,
                nyOppgaveId: OppgaveId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                clock: Clock,
            ): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling> {
                return kanOppretteNyBehandling.whenever(
                    isFalse = { KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling.left() },
                    isTrue = {
                        val opprettet = Tidspunkt.now(clock)
                        BeregnetSøknadsbehandling.Avslag(
                            id = SøknadsbehandlingId.generer(),
                            opprettet = opprettet,
                            sakId = sakId,
                            saksnummer = saksnummer,
                            søknad = søknad,
                            oppgaveId = oppgaveId,
                            fnr = fnr,
                            beregning = beregning,
                            fritekstTilBrev = fritekstTilBrev,
                            aldersvurdering = aldersvurdering,
                            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
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
                kanOppretteNyBehandling: Boolean,
                nyOppgaveId: OppgaveId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                clock: Clock,
            ): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling> {
                if (søknad is Søknad.Journalført.MedOppgave.Lukket) {
                    return KunneIkkeOppretteSøknadsbehandling.ErLukket.left()
                }
                return kanOppretteNyBehandling.whenever(
                    isFalse = { KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling.left() },
                    isTrue = {
                        val opprettet = Tidspunkt.now(clock)
                        val erAvslagGrunnetOpplysningsplikt = vilkårsvurderinger.opplysningsplikt.erAvslag

                        val oppdatertevilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.vilkår.map {
                            it.copyWithNewId()
                        }.let {
                            when (sakstype) {
                                Sakstype.ALDER -> VilkårsvurderingerSøknadsbehandling.Alder(
                                    formue = it.filterIsInstance<FormueVilkår>().single(),
                                    lovligOpphold = it.filterIsInstance<LovligOppholdVilkår>().single(),
                                    fastOpphold = it.filterIsInstance<FastOppholdINorgeVilkår>().single(),
                                    institusjonsopphold = it.filterIsInstance<InstitusjonsoppholdVilkår>()
                                        .single(),
                                    utenlandsopphold = it.filterIsInstance<UtenlandsoppholdVilkår>().single(),
                                    personligOppmøte = it.filterIsInstance<PersonligOppmøteVilkår>().single(),
                                    opplysningsplikt = it.filterIsInstance<OpplysningspliktVilkår>().single(),
                                    pensjon = it.filterIsInstance<PensjonsVilkår>().single(),
                                    familiegjenforening = it.filterIsInstance<FamiliegjenforeningVilkår>()
                                        .single(),
                                )

                                Sakstype.UFØRE -> VilkårsvurderingerSøknadsbehandling.Uføre(
                                    formue = it.filterIsInstance<FormueVilkår>().single(),
                                    lovligOpphold = it.filterIsInstance<LovligOppholdVilkår>().single(),
                                    fastOpphold = it.filterIsInstance<FastOppholdINorgeVilkår>().single(),
                                    institusjonsopphold = it.filterIsInstance<InstitusjonsoppholdVilkår>()
                                        .single(),
                                    utenlandsopphold = it.filterIsInstance<UtenlandsoppholdVilkår>().single(),
                                    personligOppmøte = it.filterIsInstance<PersonligOppmøteVilkår>().single(),
                                    opplysningsplikt = it.filterIsInstance<OpplysningspliktVilkår>().single(),
                                    uføre = it.filterIsInstance<UføreVilkår>().single(),
                                    flyktning = it.filterIsInstance<FlyktningVilkår>().single(),
                                )
                            }
                        }

                        val oppdaterteGrunnlag = Grunnlagsdata.create(
                            fradragsgrunnlag = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.map { it.copyWithNewId() as Fradragsgrunnlag },
                            bosituasjon = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjonSomFullstendig()
                                .map { it.copyWithNewId() as Bosituasjon.Fullstendig },
                        )

                        val oppdaterteEksterneGrunnlag = when (grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag) {
                            is StøtterHentingAvEksternGrunnlag -> grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.copyWithNewId()
                            StøtterIkkeHentingAvEksternGrunnlag -> StøtterIkkeHentingAvEksternGrunnlag
                        }

                        val oppdaterteGrunnlagdataOgVilkårsvurderinger =
                            GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
                                grunnlagsdata = oppdaterteGrunnlag,
                                vilkårsvurderinger = oppdatertevilkår,
                                eksterneGrunnlag = oppdaterteEksterneGrunnlag,
                            )

                        erAvslagGrunnetOpplysningsplikt.whenever(
                            isFalse = {
                                VilkårsvurdertSøknadsbehandling.Avslag(
                                    opprettet = opprettet,
                                    oppgaveId = oppgaveId,
                                    saksbehandler = saksbehandler,
                                    iverksattSøknadsbehandling = this,
                                    grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagdataOgVilkårsvurderinger,
                                ).right()
                            },
                            isTrue = {
                                VilkårsvurdertSøknadsbehandling.Avslag(
                                    opprettet = opprettet,
                                    oppgaveId = oppgaveId,
                                    saksbehandler = saksbehandler,
                                    iverksattSøknadsbehandling = this,
                                    grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagdataOgVilkårsvurderinger,
                                ).right()
                            },
                        )
                    },
                )
            }
        }
    }
}
