package org.mediahub.message

import java.util.Locale
import java.text.MessageFormat

import org.apache.commons.lang.ClassUtils._

/**
 * Central message service definition.
 * <p>
 * Use one of the various #resolve methods to get the messages for a specific context. Messages may be parameterized - 
 * @see java.text.MessageFormat
 * <p>
 * Use #forLocale(Locale) to get a localized instance of the messages.
 * <p>
 * Implementations only need to implement #resolveCode(String, Locale). 
 * It is also possible to implement #resolveFormat(Object, Locale) to access the object instance for which 
 * the message is resolved.
 * 
 * 
 * @author Mathias Broekelmann
 *
 * @since 19.12.2009
 *
 */
trait Messages {

    self =>

    /**
     * Convenience method to create a localized version of this messages instance.
     */
    def localized(lc: Locale) = new LocalizedMessages {
        val locale = lc

        def resolve(resolvable: MessageResolvable) = 
            self.resolve(resolvable, lc)

        override def resolve(thingToName: AnyRef): Option[String] = 
            self.resolve(thingToName, lc)

        override def resolve(thingToName: AnyRef, arguments: Seq[AnyRef]): Option[String] =
            self.resolve(thingToName, lc, arguments)
    }

    def resolve(resolvable: MessageResolvable, locale: Locale): Option[String] = {
        resolveFormat(resolvable.things, locale).map(_(resolveArguments(resolvable.arguments, locale)))
    }

    /**
     * Resolve the message for the given thing in the provided locale.
     */
    def resolve(thingToName: AnyRef, locale: Locale): Option[String] = 
        resolve(thingToName, locale, Seq.empty)

    def resolve(thingToName: AnyRef, locale: Locale, arguments: Seq[AnyRef]): Option[String] =
        resolveFormat(thingToName, locale).map(_(resolveArguments(arguments, locale)))

    /**
     * Resolve the message for the given thing in the provided locale. 
     * If no message is found the default message is returned
     */
    def resolve(thingToName: AnyRef, locale: Locale, defaultMessage: String): String = 
        resolve(thingToName, locale, defaultMessage, Seq.empty)

    /**
     * Resolve the message for the given thing in the provided locale. 
     * If no message is found the default message after applying the provided arguments is returned.
     */
    def resolve(thingToName: AnyRef, locale: Locale, defaultMessage: String, arguments: Seq[AnyRef]): String = {
        
        def format(f: Format, locale: Locale, arguments: Seq[AnyRef]) = 
            f(resolveArguments(arguments, locale))
            
        def fromDefaultMessage(defaultMessage: String, locale: Locale): Format = {
            args: Seq[AnyRef] => new MessageFormat(defaultMessage).format(args.toArray)
        }
            
        val resolvedFormat = resolveFormat(thingToName, locale).getOrElse(fromDefaultMessage(defaultMessage, locale))
        format(resolvedFormat, locale, arguments)
    }


    /**
     * Format type will get the argument list (may be empty) to produce the result message.
     */
    type Format = (Seq[AnyRef] => String)

    /**
     * Resolves Some(format) instance for a single code. Return None if nothing can be resolved.
     */
    def resolveCode(code: String, locale: Locale): Option[Format]

    /**
     * Resolves the first format instance which is not None if any can be found at all.
     */
    def resolveCodes(codes: Seq[String], locale: Locale): Option[Format] = {
        codes match {
            case head :: tail => resolveCode(head, locale).orElse(resolveCodes(tail, locale))
            case code => resolveCodes(code, locale)
        }
    }
    
    def resolveFormat(thingsToName: Seq[AnyRef], locale: Locale): Option[Format] = { 
        thingsToName match {
            case head :: tail => resolveFormat(head, locale).orElse(resolveFormat(tail, locale))
            case code => resolveFormat(code, locale)
        }
    }

    def resolveFormat(thingToName: AnyRef, locale: Locale): Option[Format] = 
        resolveCodes(codesOf(thingToName), locale)

    def codesOf(thingToName: AnyRef): Seq[String] = thingToName match {
        case mc: MessagesCode => mc.codes
        case code: String => code :: Nil
        case x => defaultCodesOf(x)
    }
    
    def defaultCodesOf(thingToName: AnyRef): Seq[String] =
        getPackageName(thingToName, "") + "." + getShortClassName(thingToName, "null") :: Nil

    /**
     * resolve any message from a MessagesResolvable from the given arguments.
     */
    private def resolveArguments(arguments: Seq[AnyRef], locale: Locale): Seq[AnyRef] =
        arguments.map(_ match {
            case resolvable: MessageResolvable => resolve(resolvable, locale)
            case other => other
        })
}

class EmptyMessages extends Messages {
    def resolveCode(code: String, locale: Locale): Option[Format] = None
}

/**
 * May be implemented by business classes to provide a custom message code.
 * 
 * @author Mathias Broekelmann
 *
 * @since 18.12.2009
 *
 */
trait MessagesCode {
    
    /**
     * Provide a single message code.
     */
    def code: String
    
    /**
     * Provide multiple message codes. By default this will return #code as a single element list.
     */
    def codes: Seq[String] = code :: Nil
}

/**
 * Convenience class which contains all parameters to resolve a message code. 
 * A MessageResolvable can also be used as an argument for an other message.
 * 
 * @param things the things to get the message for
 * @param arguments an optional sequence of arguments to use while building the message
 * @param defaultMessage an optional default message if no message was found for things.
 */
case class MessageResolvable(val things: Seq[AnyRef], val arguments: Seq[AnyRef], val defaultMessage: Option[String])

/**
 * Convenience trait to avoid to pass the locale to every #resolve method.
 * 
 * @author Mathias Broekelmann
 *
 * @since 19.12.2009
 *
 */
trait LocalizedMessages {
    
    /**
     * Provide the locale of the message from this instance.
     */
    def locale: Locale

    def resolve(resolvable: MessageResolvable): Option[String]

    def resolve(thingToName: AnyRef): Option[String] = 
        resolve(MessageResolvable(List(thingToName), Seq.empty, None))

    def resolve(thingToName: AnyRef, arguments: Seq[AnyRef]): Option[String] =
        resolve(MessageResolvable(List(thingToName), arguments, None))
}

