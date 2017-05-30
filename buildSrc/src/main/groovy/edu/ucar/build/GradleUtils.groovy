/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package edu.ucar.build

/**
 *
 * @author akhikhl
 * @author cwardgar
 */
class GradleUtils {
    static boolean instanceOf(Object obj, Class<?> clazz) {
        instanceOf(obj, clazz.name)
    }
    
    /**
     * Replacement for instanceof operator, workaround for Gradle 1.10 bug:
     * task classes defined in "build.gradle" fail instanceof check for base classes in gradle plugins.
     *
     * See https://github.com/akhikhl/gretty/issues/72#issuecomment-56130325 for more.
     * I've experienced this bug in practice, but I cannot reproduce it in a test environment.
     * Maybe it's Gretty-specific?
     */
    static boolean instanceOf(Object obj, String className) {
        derivedFrom(obj.getClass(), className)
    }
    
    static boolean derivedFrom(Class<?> targetClass, String className) {
        while (targetClass != null) {
            if (targetClass.getName() == className) {
                return true
            }
            
            for (Class intf in targetClass.getInterfaces()) {
                if (derivedFrom(intf, className)) {
                    return true
                }
            }
            targetClass = targetClass.getSuperclass()
        }
        
        return false
    }
    
    static boolean instanceOfAny(Object obj, List<String> classNames) {
        for (String className : classNames) {
            if (instanceOf(obj, className)) {
                return true
            }
        }
        return false
    }
}
