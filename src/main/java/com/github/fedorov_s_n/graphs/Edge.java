package com.github.fedorov_s_n.graphs;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Parametrized edge in directed graph
 *
 * @param <V> type of vertices parameters
 * @param <E> type of edges parameters
 */
public final class Edge<V, E> {

    Vertex<V, E> parent;
    Vertex<V, E> child;
    E parameter;

    /**
     * Create edge with null parameter between given vertices
     *
     * @param parent parent vertex, shouldn't be null
     * @param child  child vertex, shouldn't be null
     */
    public Edge(Vertex<V, E> parent, Vertex<V, E> child) {
        this(parent, child, null);
    }

    /**
     * Create edge with given parameter between given vertices
     *
     * @param parent    parent vertex, shouldn't be null
     * @param child     child vertex, shouldn't be null
     * @param parameter edge parameter
     */
    public Edge(Vertex<V, E> parent, Vertex<V, E> child, E parameter) {
        this.parent = Objects.requireNonNull(parent);
        this.child = Objects.requireNonNull(child);
        this.parameter = parameter;
    }

    /**
     * Get parent vertex
     *
     * @return the parent vertex
     */
    public Vertex<V, E> getParent() {
        return parent;
    }

    /**
     * Get child vertex
     *
     * @return the child vertex
     */
    public Vertex<V, E> getChild() {
        return child;
    }

    /**
     * Get this edge parameter
     *
     * @return the value that parameterizes this edge, can be null
     */
    public E getParameter() {
        return parameter;
    }
}
