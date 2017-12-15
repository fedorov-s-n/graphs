package com.github.fedorov_s_n.graphs.representation;

import com.github.fedorov_s_n.graphs.Edge;
import com.github.fedorov_s_n.graphs.Graph;
import com.github.fedorov_s_n.graphs.Vertex;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JPanelRepresentation<V, E> implements GraphRepresentation<JPanel, V, E> {

    private Function<V, String> vertexCaptionProducer = String::valueOf;

    private Function<E, String> edgeCaptionProducer = String::valueOf;

    private String[] colors = {"#000000", "#0000cc", "#006600", "#cc0000", "#660066", "#994400"};

    @Override
    public JPanel represent(Graph<V, E> input) {
        edu.uci.ics.jung.graph.Graph<Vertex<V, E>, Edge<V, E>> graph;
        VisualizationViewer<Vertex<V, E>, Edge<V, E>> vv;
        Layout<Vertex<V, E>, Edge<V, E>> layout;
        graph = new SparseMultigraph<>();
        input.vertices().forEach(graph::addVertex);
        input.edges().forEach(ref -> graph.addEdge(ref, ref.getParent(), ref.getChild(), EdgeType.DIRECTED));
        layout = new FRLayout<>(graph);
        DisplayMode displayMode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
        Dimension preferredSize = new Dimension(displayMode.getWidth(), displayMode.getHeight());
        final VisualizationModel<Vertex<V, E>, Edge<V, E>> visualizationModel
            = new DefaultVisualizationModel<>(layout, preferredSize);
        vv = new VisualizationViewer<>(visualizationModel, preferredSize);

        // this class will provide both label drawing and vertex shapes
        VertexLabelAsShapeRenderer<Vertex<V, E>, Edge<V, E>> vlasr
            = new VertexLabelAsShapeRenderer<>(vv.getRenderContext());
        // customize the render context
        vv.getRenderContext().setVertexLabelTransformer(node
            -> "<html><center>"
            + (node == null ? null : vertexCaptionProducer.apply(node.getParameter()))
            + "</center></html>"
        );
        vv.getRenderContext().setVertexShapeTransformer(vlasr);
        vv.getRenderer().setVertexRenderer(new GradientVertexRenderer<>(
            java.awt.Color.LIGHT_GRAY, java.awt.Color.white, false));
        vv.getRenderer().setVertexLabelRenderer(vlasr);
        java.awt.Color[] colors = Arrays.stream(getColors())
            .map(java.awt.Color::decode)
            .toArray(java.awt.Color[]::new);
        AtomicInteger colorCounter = new AtomicInteger();
        Map<Class, Color> colorMap = input.edges()
            .map(e -> e.getParameter() == null ? null : e.getParameter().getClass())
            .distinct()
            .sorted(Comparator.nullsFirst(Comparator.comparing(Class::getName)))
            .collect(Collectors.toMap(
                Function.identity(),
                s -> colors[colorCounter.getAndIncrement() % colors.length]
            ));

        Transformer<Edge<V, E>, Paint> colorer = e
            -> colorMap.get(e.getParameter() == null ? null : e.getParameter().getClass());
        vv.getRenderContext().setEdgeDrawPaintTransformer(colorer);
        vv.getRenderContext().setArrowDrawPaintTransformer(colorer);
        vv.getRenderContext().setArrowFillPaintTransformer(colorer);

        vv.getRenderContext().setEdgeLabelTransformer(ref
            -> ref.getParameter() == null ? null : edgeCaptionProducer.apply(ref.getParameter()));
        vv.setBackground(java.awt.Color.white);
        vv.setVertexToolTipTransformer(new ToStringLabeller<>());
        final DefaultModalGraphMouse graphMouse = new DefaultModalGraphMouse();
        graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
        vv.setGraphMouse(graphMouse);
        return new GraphZoomScrollPane(vv);
    }


    public Function<V, String> getVertexCaptionProducer() {
        return vertexCaptionProducer;
    }

    public void setVertexCaptionProducer(Function<V, String> vertexCaptionProducer) {
        this.vertexCaptionProducer = vertexCaptionProducer;
    }

    public Function<E, String> getEdgeCaptionProducer() {
        return edgeCaptionProducer;
    }

    public void setEdgeCaptionProducer(Function<E, String> edgeCaptionProducer) {
        this.edgeCaptionProducer = edgeCaptionProducer;
    }

    public String[] getColors() {
        return colors;
    }

    public void setColors(String[] colors) {
        this.colors = colors;
    }
}
