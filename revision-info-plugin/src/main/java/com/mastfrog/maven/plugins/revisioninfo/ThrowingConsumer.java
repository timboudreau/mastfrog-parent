package com.mastfrog.maven.plugins.revisioninfo;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingConsumer<T> {
    void accept(T obj) throws Exception;
}
