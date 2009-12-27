package de.osxp.dali.validation

case class ValidationMessage

trait ValidationCollector {
    def report(message: ValidationMessage): Unit
}

object Validate {
    class ValidationBuilder(val collector: ValidationCollector, val isValid: Boolean) {
        def apply(condition: => Boolean, m: => ValidationMessage) = {
            if(!isValid) {
                this
            } else if(!condition) {
                collector.report(m)
                new ValidationBuilder(collector, false)
            } else {
                this
            }
        }
    }
    
    implicit def verificationBuilderToBoolean(builder: ValidationBuilder): Boolean = builder.isValid
    
    def apply(collector: ValidationCollector) = new ValidationBuilder(collector, true)
}

