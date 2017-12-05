package com.github.fedorov_s_n.graphs;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.DefaultVisualizationModel;
import edu.uci.ics.jung.visualization.GraphZoomScrollPane;
import edu.uci.ics.jung.visualization.VisualizationModel;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import edu.uci.ics.jung.visualization.renderers.GradientVertexRenderer;
import edu.uci.ics.jung.visualization.renderers.VertexLabelAsShapeRenderer;
import org.apache.commons.collections15.Transformer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

/**
 * Directed graph with parametrized vertices and edges. Graph is supposed to be
 * effectively immutable though it's possible to mutate it.
 *
 * @param <V> type of vertices parameters
 * @param <E> type of edges parameters
 */
public final class Graph<V, E> implements Cloneable {

    private final List<Vertex<V, E>> vertices;
    private final List<Edge<V, E>> edges;

    /**
     * Create graph from given edge list.
     * Vertex list is calculated as all edges' parents and children.
     * Input list are neither checked nor copied.
     * User is supposed to care about ummutability himself.
     * <p>
     * Performs indexing on vertices so creating is not a functional call.
     *
     * @param edges list of edges
     */
    public Graph(List<Edge<V, E>> edges) {
        this(null, edges);
    }

    /**
     * Create graph from given vertex and edge lists.
     * Input collections are neither checked nor copied.
     * User is supposed to care about ummutability himself.
     * <p>
     * Performs indexing on vertices so creating is not a functional call.
     *
     * @param vertices list of vertices
     * @param edges    list of edges
     */
    public Graph(List<Vertex<V, E>> vertices, List<Edge<V, E>> edges) {
        this.edges = Objects.requireNonNull(edges);
        if (vertices == null) {
            vertices = new ArrayList<>();
            edges.stream().flatMap(e -> Stream.of(e.parent, e.child)).forEach(v -> v.index = 0);
            for (Edge<V, E> edge : edges) {
                if (edge.parent.index++ == 0) vertices.add(edge.parent);
                if (edge.child.index++ == 0) vertices.add(edge.child);
            }
        }
        this.vertices = vertices;
        int index = 0;
        for (Vertex<V, E> vertex : vertices) {
            vertex.children = vertex.parents = null;
            vertex.index = index++;
        }
        edges.stream()
            .collect(groupingBy(e -> e.parent.index))
            .forEach((i, l) -> this.vertices.get(i).children = l);
        edges.stream()
            .collect(groupingBy(e -> e.child.index))
            .forEach((i, l) -> this.vertices.get(i).parents = l);
    }

    /**
     * Get vertices of this graph
     *
     * @return stream of vertices
     */
    public Stream<Vertex<V, E>> vertices() {
        return vertices.stream();
    }

    /**
     * Get edges of this graph
     *
     * @return stream of edges
     */
    public Stream<Edge<V, E>> edges() {
        return edges.stream();
    }

    /**
     * Create new graph with vertices and edges parametrized by mapped values,
     * persisting graph structure. All vertices and edges are actually new
     * objects, not shared with this graph.
     *
     * @param <V2>          type of created graph vertices parameters
     * @param <E2>          type of created graph edges parameters
     * @param verticeMapper function to map this graph vertices parameters to
     *                      created graph vertices parameters
     * @param edgeMapper    function to map this graph edges parameters to created
     *                      graph edges parameters
     * @return new graph
     */
    public <V2, E2> Graph<V2, E2> map(Function<V, V2> verticeMapper, Function<E, E2> edgeMapper) {
        int size = vertices.size();
        @SuppressWarnings("unchecked")
        Vertex<V2, E2>[] array = new Vertex[size];
        for (Vertex<V, E> oldVertex : vertices) {
            Vertex<V2, E2> newVertex = new Vertex<>(verticeMapper.apply(oldVertex.parameter));
            newVertex.index = oldVertex.index;
            array[newVertex.index] = newVertex;
        }
        List<Vertex<V2, E2>> newVertices = Arrays.asList(array);
        List<Edge<V2, E2>> newEdges = edges.stream().map(e -> new Edge<>(
            array[e.parent.index],
            array[e.child.index],
            edgeMapper.apply(e.parameter)
        )).collect(Collectors.toList());
        return new Graph<>(newVertices, newEdges);
    }

    /**
     * Create new graph with vertices parametrized by mapped values,
     * persisting graph structure. All vertices and edges are actually new
     * objects, not shared with this graph.
     *
     * @param <V2>         type of created graph vertices parameters
     * @param vertexMapper function to map this graph vertices parameters to
     *                     created graph vertices parameters
     * @return new graph
     */
    public <V2> Graph<V2, E> map(Function<V, V2> vertexMapper) {
        return map(vertexMapper, Function.identity());
    }

    /**
     * Create new graph with edges parametrized by mapped values,
     * persisting graph structure. All vertices and edges are actually new
     * objects, not shared with this graph.
     *
     * @param <E2>       type of created graph edges parameters
     * @param edgeMapper function to map this graph edges parameters to created
     *                   graph edges parameters
     * @return new graph
     */
    public <E2> Graph<V, E2> mapEdges(Function<E, E2> edgeMapper) {
        return map(Function.identity(), edgeMapper);
    }

    /**
     * Creates new graph that has vertices and edges of this graph that passes through collection filter.
     * Edges connected to removed vertices are removed.
     * All vertices and edges are actually new objects, not shared with this graph.
     *
     * @param vertexPredicate function that determines if vertex should persist in new graph
     * @param edgePredicate   function that determines if edge should persist in new graph
     * @return new graph
     */
    public Graph<V, E> filter(Predicate<Vertex<V, E>> vertexPredicate, Predicate<Edge<V, E>> edgePredicate) {
        List<Vertex<V, E>> vertices2 = filterVertices(vertexPredicate);
        List<Edge<V, E>> edges2 = filterEdges(edgePredicate, vertices2);
        return new Graph<>(vertices2, edges2);
    }

    /**
     * Creates new graph that has vertices of this graph that passes through collection filter.
     * Edges connected to removed vertices are removed.
     * All vertices and edges are actually new objects, not shared with this graph.
     *
     * @param vertexPredicate function that determines if vertex should persist in new graph
     * @return new graph
     */
    public Graph<V, E> filter(Predicate<Vertex<V, E>> vertexPredicate) {
        return filter(vertexPredicate, any -> true);
    }

    /**
     * Creates new graph that has edges of this graph that passes through collection filter.
     * All vertices and edges are actually new objects, not shared with this graph.
     *
     * @param edgePredicate function that determines if edge should persist in new graph
     * @return new graph
     */
    public Graph<V, E> filterEdges(Predicate<Edge<V, E>> edgePredicate) {
        return filter(any -> true, edgePredicate);
    }

    /**
     * Creates new graph that has vertices of this graph if parameter is
     * instance of specified class. Edges are changed accordingly, ruled by
     * onRemove function. All vertices and edges are actually new objects, not
     * shared with this graph.
     *
     * @param <T>   type of bound
     * @param bound class that should be ancestor of all vertices parameters
     * @param test  predicate over T
     * @return new graph
     */
    @SuppressWarnings("unchecked")
    public <T> Graph<T, E> filter(Class<T> bound, Predicate<T> test) {
        Predicate<Vertex<V, E>> filter = test == null
            ? v -> (v.parameter == null || bound.isInstance(v.parameter))
            : v -> (v.parameter == null || bound.isInstance(v.parameter)) && test.test((T) v.parameter);
        return (Graph<T, E>) filter(filter);
    }

    /**
     * Creates new graph that has vertices of this graph that passes through collection filter.
     * Removes all child and parent edges of removed vertex and creates new edges from every parent to every child.
     * Edge parameter is calculated as function of parent and child edge parameters.
     * All vertices and edges in new graph are new objects, not shared with this graph.
     *
     * @param vertexPredicate function that determines if vertex should persist in new graph
     * @param edgePredicate   function that determines if edge should persist or be generated in new graph
     * @param merger          function to calculate created edge parameter
     * @return new graph
     */
    public Graph<V, E> propagate(Predicate<Vertex<V, E>> vertexPredicate, Predicate<Edge<V, E>> edgePredicate, BiFunction<E, E, E> merger) {
        List<Vertex<V, E>> vertices2 = filterVertices(vertexPredicate);
        List<Edge<V, E>> edges2 = filterEdges(edgePredicate, vertices2);
        edges2.addAll(vertices.stream()
            .filter(v -> vertices2.get(v.index) == null)
            .flatMap(v -> v.getParentEdges().flatMap(p -> v.getChildEdges().map(c -> new Edge<>(
                p.parent,
                c.child,
                merger.apply(p.parameter, c.parameter)))))
            .filter(edgePredicate)
            .collect(Collectors.toList()));
        return new Graph<>(vertices2, edges2);
    }

    /**
     * Creates new graph with distinct parameter values.
     * Edges to different vertices that have the same parameter in this graph will link to single vertex
     * in returned graph.
     *
     * @return new graph with unique vertices
     */
    public Graph<V, E> distinct() {
        @SuppressWarnings("unchecked")
        Vertex<V, E>[] newArray = new Vertex[vertices.size()];
        List<Vertex<V, E>> uniqueVertices = new ArrayList<>();
        vertices().collect(groupingBy(v -> v.parameter)).forEach((parameter, list) -> {
            Vertex<V, E> newVertex = new Vertex<>(parameter);
            list.forEach(v -> newArray[v.index] = newVertex);
            uniqueVertices.add(newVertex);
        });
        List<Edge<V, E>> edgesList = filterEdges(any -> true, Arrays.asList(newArray));
        return new Graph<>(uniqueVertices, edgesList);
    }

    /**
     * Return new graph with unique edges.
     * Edges are supposed to be equal if they have the same vertices on both ends and the same parameter.
     *
     * @return new graph with unique edges
     */
    public Graph<V, E> distinctEdges() {
        List<Vertex<V, E>> newVertices = filterVertices(any -> true);
        List<Edge<V, E>> newEdges = new ArrayList<>();
        edges().collect(groupingBy(e -> e.parent.index, groupingBy(e -> e.child.index, groupingBy(e -> e.parameter))))
            .forEach((parent, m1) -> m1.forEach((child, m2) -> m2.forEach((parameter, list) -> {
                newEdges.add(new Edge<>(newVertices.get(parent), newVertices.get(child), parameter));
            })));
        return new Graph<>(newVertices, newEdges);
    }

    /**
     * Split this graph to connected components. Edges and Vertices are copied
     * by value and parameters are copied by reference.
     *
     * @return list of connected components
     */
    public List<Graph<V, E>> split() {
        return split(vertices, Vertex::getNeighbourNodes);
    }

    /**
     * Split this graph to strongly connected components. Edges and Vertices are copied
     * by value and parameters are copied by reference.
     *
     * @return list of strongly connected components
     */
    public List<Graph<V, E>> splitEdges() {
        ArrayDeque<Vertex<V, E>> order = new ArrayDeque<>(vertices.size());
        dfs(vertices, Vertex::getParentNodes, null, null, node -> !order.offerFirst(node), null);
        return split(order, Vertex::getChildNodes);
    }

    /**
     * Calculate topological sort of this graph vertices. Resulted graph has changed {@code vertices()} order.
     *
     * @return new graph with topologically sorted vertices or null if this graph has cycles
     */
    public Graph<V, E> topsort() {
        int size = vertices.size();
        AtomicInteger index = new AtomicInteger(size);
        int[] order = new int[size];
        boolean[] added = new boolean[size];
        Vertex<V, E> cycleMarker = dfs(
            vertices,
            Vertex::getChildNodes,
            null,
            v -> (added[v.index] = true) && v.getChildNodes().anyMatch(c -> added[c.index]),
            node -> (order[node.index] = index.decrementAndGet()) < 0,
            null
        );
        if (cycleMarker != null) return null;

        @SuppressWarnings("unchecked")
        Vertex<V, E>[] vertices2 = new Vertex[size];
        for (int i = 0; i < size; ++i) {
            Vertex<V, E> vertex = new Vertex<>(vertices.get(i).parameter);
            vertex.index = order[i];
            vertices2[order[i]] = vertex;
        }
        List<Edge<V, E>> edges2 = edges()
            .map(e -> new Edge<>(
                vertices2[order[e.parent.index]],
                vertices2[order[e.child.index]],
                e.parameter))
            .collect(Collectors.toList());
        return new Graph<>(Arrays.asList(vertices2), edges2);
    }

    /**
     * Traverse graph with depth-first search until the requested vertex is
     * found or there are no nodes to continue scanning.
     *
     * @param startNodes collection of nodes to start traverse
     * @param upd        function to return stream of vertices to go
     * @param onEnter    function that is executed when another vertex is polled
     *                   from start collection
     * @param onStart    function that is executed on enter to vertex, should
     *                   return true if vertex fits under criteria and should be returned by the
     *                   method; false otherwise
     * @param onFinish   function that is executed on exit from vertex, should
     *                   return true if vertex fits under criteria and should be returned by the
     *                   method; false otherwise
     * @param onExit     function that is executed when there are no more vertices
     *                   in current sub-traverse
     * @return the first vertex fitting under onStart or onFinish criteria or
     * null if there are no such vertex
     */
    public Vertex<V, E> dfs(
        Collection<Vertex<V, E>> startNodes,
        Function<Vertex<V, E>, Stream<Vertex<V, E>>> upd,
        Consumer<Vertex<V, E>> onEnter,
        Predicate<Vertex<V, E>> onStart,
        Predicate<Vertex<V, E>> onFinish,
        Consumer<Vertex<V, E>> onExit) {
        Predicate<Vertex<V, E>> onStartFunc = onStart == null ? v -> false : onStart;
        Predicate<Vertex<V, E>> onFinishFunc = onFinish == null ? v -> false : onFinish;
        Color[] colors = new Color[vertices.size()];
        Arrays.fill(colors, Color.white);
        for (Vertex<V, E> node : startNodes) {
            if (colors[node.index] != Color.white) continue;
            if (onEnter != null) onEnter.accept(node);
            Vertex<V, E> ret = dfs(node, upd, colors, onStartFunc, onFinishFunc);
            if (onExit != null) onExit.accept(node);
            if (ret != null) return ret;
        }
        return null;
    }

    /**
     * Visualize this graph on main desktop. For debugging purposes only, not
     * for production.
     *
     * @return this graph
     */
    public Graph<V, E> visualize() {
        edu.uci.ics.jung.graph.Graph<Vertex<V, E>, Edge<V, E>> graph;
        VisualizationViewer<Vertex<V, E>, Edge<V, E>> vv;
        Layout<Vertex<V, E>, Edge<V, E>> layout;
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
        JPanel panel = new JPanel(new BorderLayout());
        frame.setContentPane(panel);
        graph = new SparseMultigraph<>();
        vertices.forEach(graph::addVertex);
        edges.forEach(ref -> graph.addEdge(ref, ref.parent, ref.child, EdgeType.DIRECTED));
        layout = new FRLayout<>(graph);
        DisplayMode displayMode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
        Dimension preferredSize = new Dimension(displayMode.getWidth(), displayMode.getHeight());
        final VisualizationModel<Vertex<V, E>, Edge<V, E>> visualizationModel
            = new DefaultVisualizationModel<>(layout, preferredSize);
        vv = new VisualizationViewer<>(visualizationModel, preferredSize);
        GraphZoomScrollPane gzsp = new GraphZoomScrollPane(vv);
        panel.add(gzsp);
        // this class will provide both label drawing and vertex shapes
        VertexLabelAsShapeRenderer<Vertex<V, E>, Edge<V, E>> vlasr
            = new VertexLabelAsShapeRenderer<>(vv.getRenderContext());
        // customize the render context
        vv.getRenderContext().setVertexLabelTransformer(node
            -> "<html><center>" + node + "</center></html>");
        vv.getRenderContext().setVertexShapeTransformer(vlasr);
        vv.getRenderer().setVertexRenderer(new GradientVertexRenderer<>(
            java.awt.Color.LIGHT_GRAY, java.awt.Color.white, false));
        vv.getRenderer().setVertexLabelRenderer(vlasr);
        java.awt.Color[] colors = Stream
            .of("#000000", "#0000cc", "#006600", "#cc0000", "#660066", "#994400")
            .map(java.awt.Color::decode)
            .toArray(java.awt.Color[]::new);
        AtomicInteger colorCounter = new AtomicInteger();
        Map<Class, java.awt.Color> colorMap = edges.stream()
            .map(e -> e.parameter == null ? null : e.parameter.getClass())
            .distinct()
            .sorted(Comparator.nullsFirst(Comparator.comparing(Class::getName)))
            .collect(Collectors.toMap(
                Function.identity(),
                s -> colors[colorCounter.getAndIncrement() % colors.length]
            ));

        Transformer<Edge<V, E>, Paint> colorer = e
            -> colorMap.get(e.parameter == null ? null : e.parameter.getClass());
        vv.getRenderContext().setEdgeDrawPaintTransformer(colorer);
        vv.getRenderContext().setArrowDrawPaintTransformer(colorer);
        vv.getRenderContext().setArrowFillPaintTransformer(colorer);

        vv.getRenderContext().setEdgeLabelTransformer(ref
            -> ref.parameter == null ? null : ref.parameter.toString());
        vv.setBackground(java.awt.Color.white);
        vv.setVertexToolTipTransformer(new ToStringLabeller<>());
        final DefaultModalGraphMouse graphMouse = new DefaultModalGraphMouse();
        graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
        vv.setGraphMouse(graphMouse);
        frame.pack();
        frame.repaint();
        CountDownLatch latch = new CountDownLatch(1);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Graph<V, E> clone() {
        return map(Function.identity(), Function.identity());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append(' ');
        builder.append(super.toString());
        builder.append('\n');
        builder.append("nodes:\n");
        for (Vertex vertex : vertices) {
            builder.append(String.format(
                "(%d,\t%s:%s)%n",
                vertex.index,
                vertex.toString(),
                vertex.parameter == null ? "null" : vertex.parameter.getClass()
            ));
        }
        builder.append("references:\n");
        for (Edge ref : edges) {
            builder.append(String.format(
                "(%d,\t%d,\t%s:%s)%n",
                ref.parent.index,
                ref.child.index,
                ref.toString(),
                ref.parameter == null ? "null" : ref.parameter.getClass()
            ));
        }
        return builder.toString();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + vertices.size();
        hash = 37 * hash + edges.size();
        hash = 37 * hash + vertices.stream()
            .mapToInt(n -> Objects.hashCode(n.parameter))
            .sum();
        hash = 37 * hash + edges.stream()
            .mapToInt(r -> Objects.hashCode(r.parameter))
            .sum();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        // best scenario:
        // time = O(n), memory = O(1), stack = O(1)
        // good scenario:
        // time = O(n^2), memory = O(n^2), stack = O(n)
        // worst scenario:
        // time = O(n! * n^2), memory = O(n^2), stack = O(n)
        // where n is node count. 

        // filtering
        if (this == obj) {
            return true;
        }
        if (obj == null
            || getClass() != obj.getClass()
            || vertices.size() != ((Graph) obj).vertices.size()
            || edges.size() != ((Graph) obj).edges.size()
            || hashCode() != obj.hashCode()) {
            return false;
        }
        Graph<?, ?> g1 = (Graph) this;
        Graph<?, ?> g2 = (Graph) obj;

        // get node correspondence variants
        @SuppressWarnings("unchecked")
        Vertex<V, E>[] n1 = g1.vertices.toArray(new Vertex[g1.vertices.size()]);
        @SuppressWarnings("unchecked")
        Vertex<V, E>[] n2 = g2.vertices.toArray(new Vertex[g2.vertices.size()]);
        int n = n1.length;
        List<int[]> variants = new ArrayList<>();
        variants(0, n, n1, n2, variants, new int[n], new boolean[n], Objects::equals, Vertex::getParameter);
        if (variants.isEmpty()) {
            return false;
        }

        // build reference tables
        Object[][][][] refs = new Object[2][n][n][];
        List<List<? extends Edge<?, ?>>> references = Arrays.asList(g1.edges, g2.edges);
        for (int j = 0; j < 2; ++j) {
            for (Edge<?, ?> ref : references.get(j)) {
                int row = ref.parent.index;
                int col = ref.child.index;
                Object[] src = refs[j][row][col];
                Object[] dest;
                Object[] append = new Object[]{ref.parameter};
                if (src == null) {
                    dest = append;
                } else {
                    dest = new Object[src.length + append.length];
                    System.arraycopy(src, 0, dest, 0, src.length);
                    System.arraycopy(append, 0, dest, src.length, append.length);
                }
                refs[j][row][col] = dest;
            }
        }

        // check all variants
        rearrangementChecks:
        for (int[] rearrangement : variants) {
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    int row = rearrangement[i];
                    int col = rearrangement[j];
                    Object[] o1 = refs[1][row][col];
                    Object[] o2 = refs[0][i][j];
                    if (!unorderedArraysEqual(o1, o2)) {
                        continue rearrangementChecks;
                    }
                }
            }
            return true;
        }
        return false;
    }

    private boolean unorderedArraysEqual(Object[] o1, Object[] o2) {
        if (o1 == null) {
            return o2 == null;
        }
        if (o2 == null) {
            return false;
        }
        if (o1.length != o2.length) {
            return false;
        }
        if (o1.length == 0) {
            return true;
        }
        if (o1.length == 1) {
            return Objects.equals(o1[0], o2[0]);
        }
        List<int[]> variants = new ArrayList<>();
        int n = o1.length;
        variants(0, n, o1, o2, variants, new int[n], new boolean[n],
            Objects::equals, Function.identity());
        rearrangementChecks:
        for (int[] rearrangement : variants) {
            for (int i = 0; i < n; ++i) {
                int j = rearrangement[i];
                if (!Objects.equals(o1[i], o2[j])) {
                    continue rearrangementChecks;
                }
            }
            return true;
        }
        return false;
    }

    private <N, R> void variants(int row, int n, N[] n1, N[] n2,
                                 List<int[]> out, int[] state, boolean[] taken,
                                 BiPredicate<R, R> comparator, Function<N, R> getter) {
        for (int col = 0; col < n; ++col) {
            if (!taken[col] && comparator.test(getter.apply(n1[row]), getter.apply(n2[col]))) {
                state[row] = col;
                if (row + 1 == n) {
                    out.add(Arrays.copyOf(state, n));
                } else {
                    taken[col] = true;
                    variants(row + 1, n, n1, n2, out, state, taken, comparator, getter);
                    taken[col] = false;
                }
            }
        }
    }

    private List<Graph<V, E>> split(Collection<Vertex<V, E>> starts, Function<Vertex<V, E>, Stream<Vertex<V, E>>> extender) {
        List<Graph<V, E>> graphs = new ArrayList<>();
        boolean[] added = new boolean[vertices.size()];
        dfs(
            starts,
            extender,
            node -> Arrays.fill(added, false),
            node -> !(added[node.index] = true),
            node -> false,
            node -> graphs.add(filter(v -> added[v.index]))
        );
        return graphs;
    }

    private List<Vertex<V, E>> filterVertices(Predicate<Vertex<V, E>> filter) {
        int size = vertices.size();
        @SuppressWarnings("unchecked")
        Vertex<V, E>[] array = new Vertex[size];
        for (Vertex<V, E> oldVertex : vertices) {
            Vertex<V, E> newVertex = null;
            if (filter.test(oldVertex)) {
                newVertex = new Vertex<>(oldVertex.parameter);
                newVertex.index = oldVertex.index;
            }
            array[oldVertex.index] = newVertex;
        }
        return Arrays.asList(array);
    }

    private List<Edge<V, E>> filterEdges(Predicate<Edge<V, E>> filter, List<Vertex<V, E>> vertices) {
        List<Edge<V, E>> edges2 = new ArrayList<>();
        for (Edge<V, E> edge : edges) {
            Vertex<V, E> parent = vertices.get(edge.parent.index);
            Vertex<V, E> child = vertices.get(edge.child.index);
            if (parent != null && child != null && filter.test(edge)) {
                edges2.add(new Edge<>(parent, child, edge.parameter));
            }
        }
        return edges2;
    }

    private Vertex<V, E> dfs(Vertex<V, E> v, Function<Vertex<V, E>, Stream<Vertex<V, E>>> upd, Color[] colors,
                             Predicate<Vertex<V, E>> onStart, Predicate<Vertex<V, E>> onFinish) {
        Stack<Vertex<V, E>> pending = new Stack<>();
        Vertex<V, E> node = v;
        pending.add(node);
        while (!pending.isEmpty()) {
            node = pending.peek();
            switch (colors[node.index]) {
                case white:
                    colors[node.index] = Color.gray;
                    if (onStart.test(node)) {
                        return node;
                    }
                case gray:
                    Optional<Vertex<V, E>> optional = upd.apply(node)
                        .filter(n -> colors[n.index] == Color.white)
                        .findAny();
                    if (optional.isPresent()) {
                        pending.push(optional.get());
                    } else {
                        if (onFinish.test(node)) {
                            return node;
                        }
                        pending.pop();
                        colors[node.index] = Color.black;
                    }
                    break;
                case black:
                default:
                    throw new IllegalStateException();
            }
        }
        return null;
    }

    private enum Color {
        white, gray, black
    }
}
