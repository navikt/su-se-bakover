package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import java.util.UUID

fun nyOpplysningspliktGrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    beskrivelse: OpplysningspliktBeskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
): Opplysningspliktgrunnlag = Opplysningspliktgrunnlag(
    id = id,
    opprettet = opprettet,
    periode = periode,
    beskrivelse = beskrivelse,
)
