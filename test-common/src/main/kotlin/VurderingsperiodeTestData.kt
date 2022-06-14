import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.util.UUID

fun vurderingsperiodeFamiliegjenforening(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Innvilget,
    grunnlag: Grunnlag? = null,
    periode: Periode = år(2022),
) = VurderingsperiodeFamiliegjenforening.create(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    periode = periode,
).getOrFail()

fun vurderingsperiodeFlyktning(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Innvilget,
    grunnlag: FlyktningGrunnlag? = null,
    vurderingsperiode: Periode = år(2022),
) = VurderingsperiodeFlyktning.tryCreate(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()
