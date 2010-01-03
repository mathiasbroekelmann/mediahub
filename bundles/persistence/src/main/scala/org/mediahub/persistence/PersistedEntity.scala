package org.mediahub.persistence

import javax.persistence._

@MappedSuperclass
trait PersistedEntity {
    @Id
    @GeneratedValue
    val id: Integer = id
}
