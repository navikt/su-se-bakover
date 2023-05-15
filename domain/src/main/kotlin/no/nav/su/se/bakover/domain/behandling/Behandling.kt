package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

/**
 * https://jira.adeo.no/browse/BEGREP-304 og https://jira.adeo.no/browse/BEGREP-2321
 */
interface Behandling {
    val id: UUID
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val periode: Periode
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger
    val eksterneGrunnlag: EksterneGrunnlag
    val sakstype: Sakstype

    val beregning: Beregning?
    val simulering: Simulering?

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

interface BehandlingMedOppgave : Behandling {
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
