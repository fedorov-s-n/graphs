package com.github.fedorov_s_n.graphs.representation;

import com.github.fedorov_s_n.graphs.Graph;

public interface GraphRepresentation<R, V, E> {

    R represent(Graph<V, E> graph);
}
