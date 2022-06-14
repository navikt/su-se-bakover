package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.vilkår.avslåttFormueVilkår
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårAvslag
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårAvslag
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgrBrukersformue
import no.nav.su.se.bakover.test.vilkår.formuevilkårUtenEps0Innvilget
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkårMedEnsligBosituasjon
import no.nav.su.se.bakover.test.vilkår.insitusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkår.insitusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårAvslag
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.pensjonsVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import vilkår.personligOppmøtevilkårAvslag
import vilkår.personligOppmøtevilkårInnvilget
import java.util.UUID

fun vilkårsvurderingSøknadsbehandlingIkkeVurdert(): Vilkårsvurderinger.Søknadsbehandling.Uføre {
    return Vilkårsvurderinger.Søknadsbehandling.Uføre.ikkeVurdert()
}

fun vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder() = Vilkårsvurderinger.Søknadsbehandling.Alder()

fun vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder() = Vilkårsvurderinger.Søknadsbehandling.Alder(
    formue = innvilgetFormueVilkårMedEnsligBosituasjon(),
    lovligOpphold = lovligOppholdVilkårInnvilget(),
    fastOpphold = fastOppholdVilkårInnvilget(),
    institusjonsopphold = insitusjonsoppholdvilkårInnvilget(),
    utenlandsopphold = utenlandsoppholdInnvilget(),
    personligOppmøte = personligOppmøtevilkårInnvilget(),
    opplysningsplikt = tilstrekkeligDokumentert(),
    familiegjenforening = familiegjenforeningVilkårInnvilget(),
)

fun vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder() = Vilkårsvurderinger.Søknadsbehandling.Alder(
    formue = avslåttFormueVilkår(),
    lovligOpphold = lovligOppholdVilkårAvslag(),
    fastOpphold = fastOppholdVilkårAvslag(),
    institusjonsopphold = insitusjonsoppholdvilkårAvslag(),
    utenlandsopphold = utenlandsoppholdAvslag(),
    personligOppmøte = personligOppmøtevilkårAvslag(),
    opplysningsplikt = utilstrekkeligDokumentert(),
    familiegjenforening = familiegjenforeningVilkårAvslag(),
)

fun vilkårsvurderingRevurderingIkkeVurdert() = Vilkårsvurderinger.Revurdering.Uføre.ikkeVurdert()

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

fun vilkårsvurderingerAlderInnvilget(
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(
        bosituasjongrunnlagEnslig(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
        ),
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(
        id = UUID.randomUUID(),
        periode = stønadsperiode.periode,
    ),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(
        id = UUID.randomUUID(),
        periode = stønadsperiode.periode,
    ),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(
        periode = stønadsperiode.periode,
        bosituasjon = bosituasjon,
    ),
    pensjon: PensjonsVilkår = pensjonsVilkårInnvilget(
        periode = stønadsperiode.periode,
    ),
): Vilkårsvurderinger.Søknadsbehandling.Alder {
    return Vilkårsvurderinger.Søknadsbehandling.Alder(
        utenlandsopphold = utenlandsopphold,
        formue = formue,
        opplysningsplikt = opplysningsplikt,
        lovligOpphold = LovligOppholdVilkår.IkkeVurdert, // hentes behandlingsinformasjon
        fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert, // hentes behandlingsinformasjon
        institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert, // hentes behandlingsinformasjon
        personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert, // hentes behandlingsinformasjon
        pensjon = pensjon,
    ).oppdater(
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        clock = fixedClock,
    )
}
