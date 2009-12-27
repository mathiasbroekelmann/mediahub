package de.osxp.dali.persistence

import javax.persistence._

@Entity
trait PersistedEntity {
    @Id
    @GeneratedValue
    var id: Int = _
}
