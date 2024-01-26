package no.nav.su.se.bakover.test

import arrow.core.Nel
import arrow.core.getOrElse
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.Vurderingsperiode
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.Verdier
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.util.UUID

fun UføreVilkår.Vurdert.Companion.create(
    vurderingsperioder: Nel<VurderingsperiodeUføre>,
): UføreVilkår.Vurdert {
    return tryCreate(vurderingsperioder)
        .getOrElse { throw IllegalArgumentException(it.toString()) }
}

/**
 * Setter måInnhenteMerInformasjon for alle grunnlagene til 'false'
 */
fun FormueVilkår.Vurdert.Companion.createFromGrunnlag(
    grunnlag: Nel<Formuegrunnlag>,
): FormueVilkår.Vurdert =
    tryCreateFromGrunnlag(
        grunnlag = grunnlag.map { false to it },
        formuegrenserFactory = formuegrenserFactoryTestPåDato(),
    ).getOrElse { throw IllegalArgumentException(it.toString()) }

fun Formuegrunnlag.Companion.create(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt,
    periode: Periode,
    epsFormue: Verdier?,
    søkersFormue: Verdier,
    behandlingsPeriode: Periode,
): Formuegrunnlag = tryCreate(
    id = id,
    opprettet = opprettet,
    periode = periode,
    epsFormue = epsFormue,
    søkersFormue = søkersFormue,
    behandlingsPeriode = behandlingsPeriode,
).getOrElse { throw IllegalArgumentException("Kunne ikke instansiere Formuegrunnlag. Underliggende grunn: $it") }

fun Verdier.Companion.empty() = create(
    verdiIkkePrimærbolig = 0,
    verdiEiendommer = 0,
    verdiKjøretøy = 0,
    innskudd = 0,
    verdipapir = 0,
    pengerSkyldt = 0,
    kontanter = 0,
    depositumskonto = 0,
)

/**
 * checking that all the objects are deeply equal, except id, which should be not equal
 * if the ids are equal, this will fail. use another shouldBe
 */
fun FormueVilkår.shouldBeEqualToExceptId(expected: FormueVilkår) {
    when (this) {
        is FormueVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is FormueVilkår.Vurdert -> {
            expected.shouldBeTypeOf<FormueVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                actual.grunnlag.shouldBeEqualToIgnoringFields(expected.grunnlag, Formuegrunnlag::id)
                actual.grunnlag.id shouldNotBe expected.grunnlag.id
            }
        }
    }
}

fun UføreVilkår.shouldBeEqualToExceptId(expected: UføreVilkår) {
    when (this) {
        UføreVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is UføreVilkår.Vurdert -> {
            expected.shouldBeTypeOf<UføreVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                if (actual.grunnlag == null) {
                    expected.grunnlag shouldBe null
                } else {
                    actual.grunnlag!!.shouldBeEqualToIgnoringFields(expected.grunnlag!!, Formuegrunnlag::id)
                    actual.grunnlag!!.id shouldNotBe expected.grunnlag!!.id
                }
            }
        }
    }
}

fun FlyktningVilkår.shouldBeEqualToExceptId(expected: FlyktningVilkår) {
    when (this) {
        FlyktningVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is FlyktningVilkår.Vurdert -> {
            expected.shouldBeTypeOf<FlyktningVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                if (actual.grunnlag == null) {
                    expected.grunnlag shouldBe null
                } else {
                    actual.grunnlag!!.shouldBeEqualToIgnoringFields(expected.grunnlag!!, Formuegrunnlag::id)
                    actual.grunnlag!!.id shouldNotBe expected.grunnlag!!.id
                }
            }
        }
    }
}

fun LovligOppholdVilkår.shouldBeEqualToExceptId(expected: LovligOppholdVilkår) {
    when (this) {
        LovligOppholdVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is LovligOppholdVilkår.Vurdert -> {
            expected.shouldBeTypeOf<LovligOppholdVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                if (actual.grunnlag == null) {
                    expected.grunnlag shouldBe null
                } else {
                    actual.grunnlag!!.shouldBeEqualToIgnoringFields(expected.grunnlag!!, Formuegrunnlag::id)
                    actual.grunnlag!!.id shouldNotBe expected.grunnlag!!.id
                }
            }
        }
    }
}

fun FastOppholdINorgeVilkår.shouldBeEqualToExceptId(expected: FastOppholdINorgeVilkår) {
    when (this) {
        FastOppholdINorgeVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is FastOppholdINorgeVilkår.Vurdert -> {
            expected.shouldBeTypeOf<FastOppholdINorgeVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                if (actual.grunnlag == null) {
                    expected.grunnlag shouldBe null
                } else {
                    actual.grunnlag!!.shouldBeEqualToIgnoringFields(expected.grunnlag!!, Formuegrunnlag::id)
                    actual.grunnlag!!.id shouldNotBe expected.grunnlag!!.id
                }
            }
        }
    }
}

fun InstitusjonsoppholdVilkår.shouldBeEqualToExceptId(expected: InstitusjonsoppholdVilkår) {
    when (this) {
        InstitusjonsoppholdVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is InstitusjonsoppholdVilkår.Vurdert -> {
            expected.shouldBeTypeOf<InstitusjonsoppholdVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                if (actual.grunnlag == null) {
                    expected.grunnlag shouldBe null
                } else {
                    actual.grunnlag!!.shouldBeEqualToIgnoringFields(expected.grunnlag!!, Formuegrunnlag::id)
                    actual.grunnlag!!.id shouldNotBe expected.grunnlag!!.id
                }
            }
        }
    }
}

fun UtenlandsoppholdVilkår.shouldBeEqualToExceptId(expected: UtenlandsoppholdVilkår) {
    when (this) {
        UtenlandsoppholdVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is UtenlandsoppholdVilkår.Vurdert -> {
            expected.shouldBeTypeOf<UtenlandsoppholdVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                if (actual.grunnlag == null) {
                    expected.grunnlag shouldBe null
                } else {
                    actual.grunnlag!!.shouldBeEqualToIgnoringFields(expected.grunnlag!!, Formuegrunnlag::id)
                    actual.grunnlag!!.id shouldNotBe expected.grunnlag!!.id
                }
            }
        }
    }
}

fun PersonligOppmøteVilkår.shouldBeEqualToExceptId(expected: PersonligOppmøteVilkår) {
    when (this) {
        PersonligOppmøteVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is PersonligOppmøteVilkår.Vurdert -> {
            expected.shouldBeTypeOf<PersonligOppmøteVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id
                actual.grunnlag.shouldBeEqualToIgnoringFields(expected.grunnlag, Formuegrunnlag::id)
                actual.grunnlag.id shouldNotBe expected.grunnlag.id
            }
        }
    }
}

fun OpplysningspliktVilkår.shouldBeEqualToExceptId(expected: OpplysningspliktVilkår) {
    when (this) {
        OpplysningspliktVilkår.IkkeVurdert -> {
            this shouldBe expected
        }

        is OpplysningspliktVilkår.Vurdert -> {
            expected.shouldBeTypeOf<OpplysningspliktVilkår.Vurdert>()
            this.vurderingsperioder.zip(expected.vurderingsperioder).map { (actual, expected) ->
                actual.shouldBeEqualToIgnoringFields(expected, Vurderingsperiode::id, Vurderingsperiode::grunnlag)
                actual.id shouldNotBe expected.id

                actual.grunnlag.shouldBeEqualToIgnoringFields(expected.grunnlag, Formuegrunnlag::id)
                actual.grunnlag.id shouldNotBe expected.grunnlag.id
            }
        }
    }
}

fun Bosituasjon.shouldBeEqualToExceptId(expected: Bosituasjon) {
    this.shouldBeEqualToIgnoringFields(expected, Bosituasjon::id)
    this.id shouldNotBe expected.id
}

@JvmName("shouldBeEqualToExceptIdGrunnlagBosituasjon")
fun List<Bosituasjon>.shouldBeEqualToExceptId(expected: List<Bosituasjon>) {
    this.zip(expected).map { (actual, expected) ->
        actual.shouldBeEqualToExceptId(expected)
    }
}

fun Fradragsgrunnlag.shouldBeEqualToExceptId(expected: Fradragsgrunnlag) {
    this.shouldBeEqualToIgnoringFields(expected, Fradragsgrunnlag::id)
    this.id shouldNotBe expected.id
}

@JvmName("shouldBeEqualToExceptIdGrunnlagFradragsgrunnlag")
fun List<Fradragsgrunnlag>.shouldBeEqualToExceptId(expected: List<Fradragsgrunnlag>) {
    this.zip(expected).map { (actual, expected) ->
        actual.shouldBeEqualToExceptId(expected)
    }
}

fun Grunnlagsdata.shouldBeEqualToExceptId(expected: Grunnlagsdata) {
    this.bosituasjon.shouldBeEqualToExceptId(expected.bosituasjon)
    this.fradragsgrunnlag.shouldBeEqualToExceptId(expected.fradragsgrunnlag)
}

fun Vilkårsvurderinger.shouldBeEqualToExceptId(expected: Vilkårsvurderinger) {
    when (this) {
        is Vilkårsvurderinger.Revurdering.Uføre -> {
            this.shouldBeEqualToExceptId(expected)
        }

        is Vilkårsvurderinger.Søknadsbehandling.Uføre -> {
            this.shouldBeEqualToExceptId(expected)
        }

        is Vilkårsvurderinger.Revurdering.Alder -> {
            this.shouldBeEqualToExceptId(expected)
        }

        is Vilkårsvurderinger.Søknadsbehandling.Alder -> {
            this.shouldBeEqualToExceptId(expected)
        }
    }
}

fun Vilkårsvurderinger.Søknadsbehandling.Uføre.shouldBeEqualToExceptId(expected: Vilkårsvurderinger) {
    expected.shouldBeType<Vilkårsvurderinger.Søknadsbehandling.Uføre>().let {
        this.uføre.shouldBeEqualToExceptId(it.uføre)
        this.formue.shouldBeEqualToExceptId(it.formue)
        this.flyktning.shouldBeEqualToExceptId(it.flyktning)
        this.lovligOpphold.shouldBeEqualToExceptId(it.lovligOpphold)
        this.fastOpphold.shouldBeEqualToExceptId(it.fastOpphold)
        this.institusjonsopphold.shouldBeEqualToExceptId(it.institusjonsopphold)
        this.utenlandsopphold.shouldBeEqualToExceptId(it.utenlandsopphold)
        this.personligOppmøte.shouldBeEqualToExceptId(it.personligOppmøte)
    }
}

fun Vilkårsvurderinger.Søknadsbehandling.Alder.shouldBeEqualToExceptId(expected: Vilkårsvurderinger) {
    expected.shouldBeType<Vilkårsvurderinger.Søknadsbehandling.Alder>().let {
        this.formue.shouldBeEqualToExceptId(it.formue)
        this.lovligOpphold.shouldBeEqualToExceptId(it.lovligOpphold)
        this.fastOpphold.shouldBeEqualToExceptId(it.fastOpphold)
        this.institusjonsopphold.shouldBeEqualToExceptId(it.institusjonsopphold)
        this.utenlandsopphold.shouldBeEqualToExceptId(it.utenlandsopphold)
        this.personligOppmøte.shouldBeEqualToExceptId(it.personligOppmøte)
    }
}

fun Vilkårsvurderinger.Revurdering.Uføre.shouldBeEqualToExceptId(expected: Vilkårsvurderinger) {
    expected.shouldBeType<Vilkårsvurderinger.Revurdering.Uføre>().let {
        this.uføre.shouldBeEqualToExceptId(it.uføre)
        this.formue.shouldBeEqualToExceptId(it.formue)
        this.utenlandsopphold.shouldBeEqualToExceptId(it.utenlandsopphold)
        this.opplysningsplikt.shouldBeEqualToExceptId(it.opplysningsplikt)
    }
}

fun Vilkårsvurderinger.Revurdering.Alder.shouldBeEqualToExceptId(expected: Vilkårsvurderinger) {
    expected.shouldBeType<Vilkårsvurderinger.Revurdering.Alder>().let {
        this.formue.shouldBeEqualToExceptId(it.formue)
        this.utenlandsopphold.shouldBeEqualToExceptId(it.utenlandsopphold)
        this.opplysningsplikt.shouldBeEqualToExceptId(it.opplysningsplikt)
    }
}
