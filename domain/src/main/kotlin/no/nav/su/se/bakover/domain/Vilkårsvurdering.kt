package no.nav.su.se.bakover.domain

class Vilkårsvurdering(
    id: Long,
    private val vilkår: Vilkår,
    private val begrunnelse: String,
    private val status: Status
) : PersistentDomainObject<VoidObserver>(id) {
    //language=JSON
    fun toJson() = """
        {
            "vilkår": "$vilkår",
            "begrunnelse" : "$begrunnelse",
            "status": "$status"
        }
    """.trimIndent()

    enum class Status {
        OK,
        IKKE_OK,
        IKKE_VURDERT
    }
}

enum class Vilkår {
    UFØRE
}
