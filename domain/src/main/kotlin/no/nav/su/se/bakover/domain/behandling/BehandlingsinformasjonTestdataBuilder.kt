package no.nav.su.se.bakover.domain.behandling

import org.jetbrains.annotations.TestOnly

/**
 * Dette er kanskje ikke den beste plassen å legge ting som kun skal brukes i tester.
 * Se også SøknadInnholdTestdataBuilder
 */
@TestOnly
fun Behandlingsinformasjon.withAlleVilkårOppfylt() =
    Behandlingsinformasjon(
        fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
        ),
        institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
        ),
    )

@TestOnly
fun Behandlingsinformasjon.withAlleVilkårAvslått() =
    Behandlingsinformasjon(
        fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
        ),
        institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårIkkeOppfylt,
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
        ),
    )

@TestOnly
fun Behandlingsinformasjon.withAvslåttPersonligOppmøte(): Behandlingsinformasjon {
    return withAlleVilkårOppfylt().patch(
        Behandlingsinformasjon(
            personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            ),
        ),
    )
}
