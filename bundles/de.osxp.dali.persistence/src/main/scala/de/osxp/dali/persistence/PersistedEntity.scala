package de.osxp.dali.persistence

import javax.persistence._

@MappedSuperclass
trait PersistedEntity {
    @Id
    @GeneratedValue
    val id: Integer = id
}
