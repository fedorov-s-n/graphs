package com.github.fedorov_s_n.graphs;

import java.util.List;
import java.util.stream.Stream;

/**
 * Parametrized vertex in directed graph
 *
 * @param <V> type of vertices parameters
 * @param <E> type of edges parameters
 */
public final class Vertex<V, E> {

    List<Edge<V, E>> parents;
    List<Edge<V, E>> children;
    V parameter;
    int index;

    /**
     * Create vertex with null parameter
     */
    public Vertex() {
    }

    /**
     * Create vertex with given parameter
     *
     * @param parameter value to parameterize created vertex
     */
    public Vertex(V parameter) {
        this.parameter = parameter;
    }

    /**
     * Get both parent and child edges of this vertex
     *
     * @return stream of parent and child edges
     */
    public Stream<Edge<V, E>> getNeighbourEdges() {
        return Stream.concat(getParentEdges(), getChildEdges());
    }

    /**
     * Get both parent and child vertices of this vertex
     *
     * @return stream of parent and child vertices
     */
    public Stream<Vertex<V, E>> getNeighbourNodes() {
        return Stream.concat(getParentNodes(), getChildNodes());
    }

    /**
     * Get parent edges of this vertex
     *
     * @return stream of parent edges
     */
    public Stream<Edge<V, E>> getParentEdges() {
        return parents == null
            ? Stream.empty()
            : parents.stream();
    }

    /**
     * Get parent vertices of this vertex
     *
     * @return stream of parent vertices
     */
    public Stream<Vertex<V, E>> getParentNodes() {
        return getParentEdges().map(Edge::getParent);
    }

    /**
     * Get child edges of this vertex
     *
     * @return stream of child edges
     */
    public Stream<Edge<V, E>> getChildEdges() {
        return children == null
            ? Stream.empty()
            : children.stream();
    }

    /**
     * Get child vertices of this vertex
     *
     * @return stream of child vertices
     */
    public Stream<Vertex<V, E>> getChildNodes() {
        return getChildEdges().map(Edge::getChild);
    }

    /**
     * Get value that parametrizes this vertex
     *
     * @return this vertex parameter
     */
    public V getParameter() {
        return parameter;
    }
}
