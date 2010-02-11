/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mediahub.sandbox;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author mathias.broekelmann
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
@Target(ElementType.TYPE)
public @interface ContentBeanBinding {

    /**
     * define the name of the doctype to which this content bean is bound to
     */
    String value();
}
