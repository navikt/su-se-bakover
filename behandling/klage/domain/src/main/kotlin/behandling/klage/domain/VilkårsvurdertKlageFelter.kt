package behandling.klage.domain

import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk

interface VilkårsvurdertKlageFelter : Klagefelter {
    val vilkårsvurderinger: VilkårsvurderingerTilKlage
    val attesteringer: Attesteringshistorikk
}
