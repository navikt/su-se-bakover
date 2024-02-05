package no.nav.su.se.bakover.behandling

import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import vilkår.vurderinger.domain.Vilkårsvurderinger
import økonomi.domain.simulering.Simulering
import java.util.UUID

/**
 * https://jira.adeo.no/browse/BEGREP-304 og https://jira.adeo.no/browse/BEGREP-2321
 */
interface Stønadsbehandling : Behandling {
    override val id: BehandlingsId
    override val opprettet: Tidspunkt
    override val sakId: UUID
    override val saksnummer: Saksnummer
    override val fnr: Fnr
    val periode: Periode
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger

    val sakstype: Sakstype

    val beregning: Beregning?
    val simulering: Simulering?

    val grunnlagsdata: Grunnlagsdata get() = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
    val eksterneGrunnlag: EksterneGrunnlag get() = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag
    fun sakinfo(): SakInfo {
        return SakInfo(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            type = sakstype,
        )
    }

    fun skalSendeVedtaksbrev(): Boolean
}

interface BehandlingMedOppgave : Stønadsbehandling {
    val oppgaveId: OppgaveId
}

interface BehandlingMedAttestering : Behandling {
    val attesteringer: Attesteringshistorikk

    fun hentAttestantSomIverksatte(): NavIdentBruker.Attestant? {
        return this.attesteringer.hentSisteIverksatteAttesteringOrNull()?.attestant
    }

    fun prøvHentSisteAttestering(): Attestering? = attesteringer.prøvHentSisteAttestering()
    fun prøvHentSisteAttestant(): NavIdentBruker.Attestant? = prøvHentSisteAttestering()?.attestant
}
