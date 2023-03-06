package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
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
    val sakstype: Sakstype

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

    fun prøvHentSisteAttestering(): Attestering? = attesteringer.prøvHentSisteAttestering()
    fun prøvHentSisteAttestant(): NavIdentBruker.Attestant? = prøvHentSisteAttestering()?.attestant
}
