package no.nav.su.se.bakover.database.jobcontext

import no.nav.su.se.bakover.domain.jobcontext.JobContext

interface JobContextDb {
    fun id(): String
    fun toJson(): String
    fun toDomain(): JobContext
}
