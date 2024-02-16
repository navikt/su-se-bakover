package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.pensjon.domain.Pensjonsgrunnlag
import vilkår.pensjon.domain.Pensjonsopplysninger
import java.util.UUID

fun nyPensjonsgrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    pensjonsopplysninger: Pensjonsopplysninger = Pensjonsopplysninger(
        søktPensjonFolketrygd = Pensjonsopplysninger.SøktPensjonFolketrygd(Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden),
        søktAndreNorskePensjoner = Pensjonsopplysninger.SøktAndreNorskePensjoner(Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarSøktAndreNorskePensjonerEnnFolketrygden),
        søktUtenlandskePensjoner = Pensjonsopplysninger.SøktUtenlandskePensjoner(Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarSøktUtenlandskePensjoner),
    ),
): Pensjonsgrunnlag = Pensjonsgrunnlag(
    id = id,
    opprettet = opprettet,
    periode = periode,
    pensjonsopplysninger = pensjonsopplysninger,
)
