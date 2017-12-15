package com.github.fedorov_s_n.graphs.representation;

import com.github.fedorov_s_n.graphs.Graph;

public interface GraphRestorableRepresentation<R, V, E> extends GraphRepresentation<R, V, E> {

    Graph<V, E> restore(R representation);
}
