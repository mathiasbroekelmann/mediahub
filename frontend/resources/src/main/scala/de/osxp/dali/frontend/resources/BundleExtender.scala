package de.osxp.dali.frontend.resources

import org.osgi.framework._

import com.google.inject._

import org.ops4j.peaberry.activation.{Start, Stop}

import scala.collection.mutable.{Map, Set}

trait BundleExtender {
    
    @Inject
    val context: BundleContext = context
    
    /**
     * factory method for instances of extender.
     */
    val extenderBuilder: (BundleContext => Extender)
    
    /**
     * should we call Activation#deactivate when this bundle is stopped? Default is true
     */
    val deactivateOnStop = true
    
    private[this] val activations: Map[Bundle, Activation] = Map.empty
    
    def activate(extender: Option[Extender], bundle: Bundle) {
        for(ext <- extender) {
            val activation = ext.activate(bundle)
            
            activations.synchronized {
                activations += bundle -> activation
            }
        }
    }
    
    def deactivate(extender: Option[Extender], bundle: Bundle) {
        val activation = activations.synchronized { activations.remove(bundle) }
        for(someActivation <- activation) {
            someActivation.deactivate
        }
    }

    private[this] val registry = new {
        def register(bundle: Bundle) {
            maybeActivate(bundle) { activate(extender, bundle) }
        }
        
        def unregister(bundle: Bundle) {
            maybeDeactivate(bundle) { deactivate(extender, bundle) }
        }
        
        /**
         * collect the activated bundles to deactivate each bundle.
         */
        def close {
            var activatedBundles: Seq[Bundle] = Seq.empty
            withActivations (bundles => {
                activatedBundles = bundles.result.toSeq
                bundles.clear
                true
            }, {
                if(deactivateOnStop) {
                    activatedBundles foreach( deactivate(extender, _))
                }
            })
        }
    }
    
    def maybeActivate[A<:Any](bundle: Bundle)(f: => A): Option[A] = {
        withActivations(_.add(bundle), f)
    }

    def maybeDeactivate[A<:Any](bundle: Bundle)(f: => A): Option[A] = {
        withActivations(_.contains(bundle), f)
    }
    
    /**
     * store all activated bundles in this mutable set
     */
    private[this] val activatedBundles: Set[Bundle] = Set.empty
    
    /**
     * Calls function f only if check resolves to true. the check function is executed in a synchronized block.
     * 
     * TODO: find out if actors where of help here.
     */
    def withActivations[A<:Any](check: Set[Bundle] => Boolean, f: => A): Option[A] = {
        val execute = activatedBundles.synchronized { check(activatedBundles) }
        if (execute) Some(f) else None
    }
    
    val bundleListener: BundleListener = new BundleListener {
        def bundleChanged(event: BundleEvent) {
            event.getType match {
                case BundleEvent.STARTED => registry.register(event.getBundle)
                case BundleEvent.STOPPED => registry.unregister(event.getBundle)
                case _ =>
            }
        }
    }
    
    private[this] var extender: Option[Extender] = None
    
    @Start
    def start {
        extender = Option(extenderBuilder(context))
        context.addBundleListener(bundleListener)
        for(bundle <- context.getBundles; if ((bundle.getState & (Bundle.STARTING | Bundle.ACTIVE)) != 0)) {
            registry register bundle
        }
    }

    @Stop
    def stop {
        registry.close
        extender.foreach(_.close)
    }
}

/**
 * A scoped instance of an extender.
 * 
 * @author Mathias Broekelmann
 *
 * @since 22.12.2009
 *
 */
trait Extender {
    /**
     * called when a bundle is activated.
     */
    val activate: Bundle => Activation
    
    /**
     * called when the extender is shut down. Implementations must not deactivate any activations. 
     * this method is only for additional clean up of the extender.
     */
    def close: Unit = {}
}


/**
 * An activation handle.
 */
trait Activation {
    
    /**
     * called to deactivate this handle.
     */
    def deactivate: Unit
}