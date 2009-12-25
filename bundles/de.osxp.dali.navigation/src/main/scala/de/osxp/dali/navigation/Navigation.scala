package de.osxp.dali.navigation

import scala.math.Ordering

import com.google.inject.{Inject}
import com.google.inject.name.{Named}

import org.apache.commons.lang.ClassUtils._

import java.io._

/**
 * Contract for a navigation service. Implementations define how the navigation is build.
 */
trait NavigationService {

    /**
     * Returns the root navigation point.
     */
    def root: NavigationPoint

    /**
     * Returns the navigation point for given definition.
     * 
     * @param active the active navigation point.
     */
    def create(definition: NavigationPointDefinition): NavigationPoint
}

/**
 * Default implementation of the navigation service.
 */
class DefaultNavigationService @Inject() (val definitions: Seq[NavigationPointDefinition]) extends NavigationService {
    
    def root: NavigationPoint = create(Root)

    def create(definition: NavigationPointDefinition): NavigationPoint = {

        /**
         * Resolves the defined childs of the given navigation point
         */
        def childsOf(np: NavigationPointDefinition): Seq[NavigationPointDefinition] = 
            definitions.filter(_.parent.map(_ == np).getOrElse(false))

        /**
         * factory method for instances of NavigationPoint
         */
        def create(navPointDef: NavigationPointDefinition, 
                   active: (NavigationPointDefinition => Boolean), 
                   open: (NavigationPointDefinition => Boolean)): NavigationPoint = {
            new NavigationPoint {
                val definition = navPointDef
                lazy val parent = navPointDef.parent.map(p => create(p, active, open))
                lazy val isActive = active(navPointDef)
                lazy val isOpen = open(navPointDef)
                lazy val childs = navPointDef.children(childsOf(navPointDef)).map(create(_, active, open))
            }
        }

        def isOpen = { 
            np: NavigationPointDefinition => definition.path.contains(np)
        }

        def isActive = { 
            np: NavigationPointDefinition => np == definition
        }

        // the actual nav point
        create(definition, isActive, isOpen)
    }
}

/**
 * Defines a navigation point. Each navigation point has a parent. Only root has no parent.
 * 
 * @author Mathias Broekelmann
 *
 * @since 18.12.2009
 *
 */
case class NavigationPointDefinition(val parent: Option[NavigationPointDefinition]) extends Equals {
    
    self =>

    /**
     * convenience constructor for non root nav points.
     */
    def this(parent: NavigationPointDefinition) = this(Option(parent))
    
    /**
     * Provide the children of this navigation point.
     * 
     * Subclasses may add or completely define its own logic to provide children.
     * Implement this method to dynamically define navigation points.
     * It is also possible to order the navigation points. See OrderedChildren mixin trait.
     * 
     * @param definedChildren children known at definition time.
     */
    def children(definedChildren: Seq[NavigationPointDefinition]) = definedChildren
    
    /**
     * Returns true if the given nav point is a parent of this nav point. 
     */
    def isParent(navpoint: NavigationPointDefinition): Boolean = {
        parents.contains(navpoint)
    }
    
    /**
     * Provides a sequence of all parent nav points from top (root) to bottom (parent of this nav point).
     */
    lazy val parents: Seq[NavigationPointDefinition] = {
        parent.map(_.path).getOrElse(Nil)
    }

    /**
     * Provides a sequence of all nav points from top (root) to bottom (this nav point).
     * 
     * @return never null and never empty. contains at least this nav point definition.
     */
    lazy val path: Seq[NavigationPointDefinition] = {
        parent.map(_.path).getOrElse(Nil) ++ (self :: Nil)
    }
    
    /**
     * Resolves the root navpoint.
     */
    lazy val root = path.head
    
    override def toString: String = {
        getShortClassName(getClass)
    }
    
    override def equals(other: Any): Boolean = {
        other match {
            case that: NavigationPointDefinition => 
                (that canEqual this) && that.getClass == getClass
            case _ => false
        }
    }
    
    override def hashCode: Int = {
        getClass.hashCode
    }
}

/**
 * Composition trait to be mixed in to order the child navigation points.
 */
trait OrderedChildren extends NavigationPointDefinition {
    
    @Inject
    val ordering: Ordering[NavigationPointDefinition] = ordering
    
    /**
     * orders the given nav points by the defined ordering
     */
    abstract override def children(navPoints: Seq[NavigationPointDefinition]) = {
        super.children(if(ordering == null) navPoints else navPoints.sortWith(ordering))
    }
}

/**
 * Root navigation point definition.
 */
object Root extends NavigationPointDefinition(None) with OrderedChildren

/**
 * A NavigationPoint provides the contract for a single nav point in the navigation.
 */
trait NavigationPoint {
    
    self =>
    
    /**
     * The definition on which this navigation point is created for.
     */
    val definition: NavigationPointDefinition

    /**
     * The parent of the navigation point. None if there is no parent.
     */
    def parent: Option[NavigationPoint]

    /**
     * Returns the list of parents from top (root) to bottom (excluding this nav point).
     */
    lazy val parents: Seq[NavigationPoint] = {
        parent.map(_.path).getOrElse(Nil)
    }

    /**
     * Returns the list of nav points from top (root) to bottom (including this nav point).
     */
    lazy val path: Seq[NavigationPoint] = {
        parent.map(_.path).getOrElse(Nil) ++ (self :: Nil)
    }
    
    lazy val root = path.head

    /**
     * The childs of the navigation point. May be empty if there are no childs.
     */
    def childs: Seq[NavigationPoint]

    /**
     * Returns true if the state of this nav point is open in the current context.
     */
    def isOpen: Boolean

    /**
     * Returns true if the state of this nav point is active in the current context. 
     * This is normally the case if the nav point is selected.
     */
    def isActive: Boolean
    
    /**
     * return the string representation of this nav point. 
     */
    override def toString: String = {
        val out = new StringWriter
        printTo(0, new PrintWriter(out))
        out.toString
    }

    protected def printTo(pad: Int, out: PrintWriter): Unit = {
        out.append(definition.toString)
        if(isActive) out.append(" active")
        if(isOpen) { 
            out.append(" open")
            for (child <- childs) {
                out.println
                out.append(for(x <- 1 to pad + 2) yield (' '))
                child.printTo(pad + 2, out)
            }
        }
    }
}

/**
 * Mixin trait to provide a navigation point.
 */
trait Navigation {
    
    /**
     * a navigation point. Never null.
     */
    def navigationPoint: NavigationPoint
}