package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.periode
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgrBrukersformue
import no.nav.su.se.bakover.test.vilkår.formuevilkårUtenEps0Innvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import java.util.UUID

fun vilkårsvurderingSøknadsbehandlingIkkeVurdert(): Vilkårsvurderinger.Søknadsbehandling.Uføre {
    return Vilkårsvurderinger.Søknadsbehandling.Uføre.ikkeVurdert()
}

fun vilkårsvurderingRevurderingIkkeVurdert(): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre.ikkeVurdert()
}

/**
 * periode: 2021
 * uføre: innvilget med forventet inntekt 0
 * bosituasjon: enslig
 * formue (via behandlingsinformasjon): ingen eps, sum 0
 * utenlandsopphold: innvilget
 */
fun vilkårsvurderingerSøknadsbehandlingInnvilget(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(
        bosituasjongrunnlagEnslig(
            id = UUID.randomUUID(),
            periode = periode,
        ),
    ),
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
): Vilkårsvurderinger.Søknadsbehandling.Uføre {
    return Vilkårsvurderinger.Søknadsbehandling.Uføre(
        uføre = uføre,
        utenlandsopphold = utenlandsopphold,
        formue = formue,
        opplysningsplikt = opplysningsplikt,
        lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
        fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
        institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
        personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
        flyktning = FlyktningVilkår.IkkeVurdert,
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(periode = periode),
        behandlingsinformasjon = behandlingsinformasjon,
        clock = fixedClock,
    )
}

fun vilkårsvurderingerRevurderingInnvilget(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(periode = periode),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(
        periode = periode,
        bosituasjon = bosituasjon,
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(periode = periode),
): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre(
        uføre = uføre,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
    )
}

fun vilkårsvurderingerAvslåttAlleRevurdering(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = avslåttUførevilkårUtenGrunnlag(periode = periode),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
    formue: Vilkår.Formue = formuevilkårAvslåttPgrBrukersformue(
        periode = periode,
        bosituasjon = NonEmptyList.fromListUnsafe(bosituasjon.toList()),
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdAvslag(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = utilstrekkeligDokumentert(periode = periode),
): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre(
        uføre = uføre,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
    )
}

/**
 * Defaults:
 * periode: 2021
 * bosituasjon: enslig
 *
 * Predefined:
 * uføre: avslag
 * formue: innvilget
 */
@Suppress("unused")
fun vilkårsvurderingerAvslåttAlle(
    periode: Periode = år(2021),
): Vilkårsvurderinger.Søknadsbehandling.Uføre {
    return Vilkårsvurderinger.Søknadsbehandling.Uføre(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        utenlandsopphold = utenlandsoppholdAvslag(periode = periode),
        formue = formuevilkårAvslåttPgrBrukersformue(
            periode = periode,
            bosituasjon = bosituasjongrunnlagEnslig(periode = periode),
        ),
        opplysningsplikt = utilstrekkeligDokumentert(periode = periode),
        // Disse blir oppdatert fra [behandlingsinformasjonAlleVilkårAvslått]
        lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
        fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
        institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
        personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
        flyktning = FlyktningVilkår.IkkeVurdert,
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(periode = periode),
        behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
        clock = fixedClock,
    )
}

/**
 * Defaults:
 * periode: 2021
 * bosituasjon: enslig
 *
 * Predefined:
 * uføre: avslag
 * formue: innvilget
 */
fun vilkårsvurderingerAvslåttUføreOgAndreInnvilget(
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre(
        uføre = avslåttUførevilkårUtenGrunnlag(periode = periode),
        formue = formuevilkårUtenEps0Innvilget(
            periode = periode,
            bosituasjon = bosituasjon,
        ),
        utenlandsopphold = utenlandsoppholdInnvilget(periode = periode),
        opplysningsplikt = tilstrekkeligDokumentert(periode = periode),
    )
}
