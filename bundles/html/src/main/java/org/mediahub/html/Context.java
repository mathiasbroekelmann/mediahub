/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mediahub.html;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Primary used to annotated a dependency binding for a value that is dependend on the current computation context.
 *
 * @author mathias.broekelmann
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Context {
}
