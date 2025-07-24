package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.nonEmptyListOf
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import org.junit.jupiter.api.Test
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt

class IverksattSøknadsbehandlingTest {

    @Test
    fun `et avslag med beregning kan opprette ny behandling - innholdet i nye blir for det meste en kopi`() {
        val (sak, original) = søknadsbehandlingIverksattAvslagMedBeregning()

        original.opprettNySøknadsbehandling(
            nyOppgaveId = OppgaveId(value = "ny oppgaveId"),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
            clock = fixedClock,
            årsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
            omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
        ).getOrFail().let {
            it.shouldBeEqualToExceptId(
                BeregnetSøknadsbehandling.Avslag(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    søknad = original.søknad,
                    oppgaveId = OppgaveId(value = "ny oppgaveId"),
                    fnr = sak.fnr,
                    beregning = original.beregning,
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
                    attesteringer = Attesteringshistorikk.empty(),
                    søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
                        Søknadsbehandlingshendelse(
                            handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(original.id),
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
                            tidspunkt = fixedTidspunkt,
                        ),
                    ),
                    fritekstTilBrev = original.fritekstTilBrev,
                    aldersvurdering = original.aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = original.grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sak.type,
                    omgjøringsårsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
                    omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
                ),
            )
        }
    }

    @Test
    fun `et avslag uten beregning (pga opplysningsplikt) kan opprette ny behandling - vi innvilger opplysningsplikt, resten er for det meste kopi`() {
        val (sak, original) = søknadsbehandlingIverksattAvslagUtenBeregning(
            customVilkår = listOf(
                institusjonsoppholdvilkårInnvilget(),
                utilstrekkeligDokumentert(),
            ),
        )

        original.opprettNySøknadsbehandling(
            nyOppgaveId = OppgaveId(value = "ny oppgaveId"),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
            clock = fixedClock,
            årsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
            omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
        ).getOrFail().let {
            it.shouldBeEqualToExceptId(
                VilkårsvurdertSøknadsbehandling.Innvilget(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    søknad = original.søknad,
                    oppgaveId = OppgaveId(value = "ny oppgaveId"),
                    fnr = sak.fnr,
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
                    attesteringer = Attesteringshistorikk.empty(),
                    søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
                        Søknadsbehandlingshendelse(
                            handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(original.id),
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
                            tidspunkt = fixedTidspunkt,
                        ),
                    ),
                    fritekstTilBrev = original.fritekstTilBrev,
                    aldersvurdering = original.aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = original.grunnlagsdataOgVilkårsvurderinger.copy(
                        vilkårsvurderinger = (original.vilkårsvurderinger as VilkårsvurderingerSøknadsbehandling.Uføre).copy(
                            opplysningsplikt = OpplysningspliktVilkår.Vurdert.tryCreate(
                                vurderingsperioder = nonEmptyListOf(
                                    VurderingsperiodeOpplysningsplikt.create(
                                        id = (it.grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.opplysningsplikt as OpplysningspliktVilkår.Vurdert).vurderingsperioder.single().id,
                                        opprettet = fixedTidspunkt,
                                        periode = år(2021),
                                        grunnlag = Opplysningspliktgrunnlag(
                                            id = (it.grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.opplysningsplikt as OpplysningspliktVilkår.Vurdert).vurderingsperioder.single().grunnlag.id,
                                            opprettet = fixedTidspunkt,
                                            periode = år(2021),
                                            beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                                        ),
                                    ),
                                ),
                            ).getOrFail(),
                        ),
                    ),
                    sakstype = sak.type,
                    omgjøringsårsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
                    omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
                ),
            )
        }
    }

    @Test
    fun `et avslag uten beregning (pga vilkår annet en opplysningsplikt) kan opprette ny behandling`() {
        val (sak, original) = søknadsbehandlingIverksattAvslagUtenBeregning()

        original.opprettNySøknadsbehandling(
            nyOppgaveId = OppgaveId(value = "ny oppgaveId"),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
            clock = fixedClock,
            årsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
            omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
        ).getOrFail().let {
            it.shouldBeEqualToExceptId(
                VilkårsvurdertSøknadsbehandling.Avslag(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    søknad = original.søknad,
                    oppgaveId = OppgaveId(value = "ny oppgaveId"),
                    fnr = sak.fnr,
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
                    attesteringer = Attesteringshistorikk.empty(),
                    søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
                        Søknadsbehandlingshendelse(
                            handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(original.id),
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
                            tidspunkt = fixedTidspunkt,
                        ),
                    ),
                    fritekstTilBrev = original.fritekstTilBrev,
                    aldersvurdering = original.aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = original.grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sak.type,
                    omgjøringsårsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
                    omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
                ),
            )
        }
    }
}
