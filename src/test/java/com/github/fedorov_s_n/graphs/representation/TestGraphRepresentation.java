package com.github.fedorov_s_n.graphs.representation;

import com.github.fedorov_s_n.graphs.Edge;
import com.github.fedorov_s_n.graphs.Graph;
import com.github.fedorov_s_n.graphs.Vertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestGraphRepresentation implements GraphRestorableRepresentation<String, Integer, Integer> {

    private static Pattern SEQUENCE_PATTERN = Pattern.compile(",");
    private static Pattern EDGE_PATTERN = Pattern.compile("(-/([0-9]*)/)?->");

    @Override
    public String represent(Graph<Integer, Integer> graph) {
        StringBuilder builder = new StringBuilder();
        graph.edges().forEach(e -> {
            if (builder.length() != 0) builder.append(", ");
            builder.append(e.getParent().getParameter());
            builder.append("->");
            builder.append(e.getChild().getParameter());
        });
        return builder.toString();
    }

    @Override
    public Graph<Integer, Integer> restore(String representation) {
        Map<Integer, Vertex<Integer, Integer>> allVertices = new HashMap<>();
        List<Edge<Integer, Integer>> allEdges = new ArrayList<>();
        SEQUENCE_PATTERN.splitAsStream(representation).forEach(part -> {
            Matcher matcher = EDGE_PATTERN.matcher(part);
            List<Integer> vertexParameters = new ArrayList<>();
            List<Integer> edgeParameters = new ArrayList<>();
            int lastEnd = 0;
            for (; matcher.find(); lastEnd = matcher.end()) {
                vertexParameters.add(Integer.parseInt(part.substring(lastEnd, matcher.start()).trim()));
                String edgeParameter = matcher.group(2);
                edgeParameters.add(edgeParameter == null ? null : Integer.parseInt(edgeParameter));
            }
            vertexParameters.add(Integer.parseInt(part.substring(lastEnd).trim()));
            for (int i = 0; i < edgeParameters.size(); ++i) {
                allEdges.add(new Edge<>(
                    allVertices.computeIfAbsent(vertexParameters.get(i), Vertex<Integer, Integer>::new),
                    allVertices.computeIfAbsent(vertexParameters.get(i + 1), Vertex<Integer, Integer>::new),
                    edgeParameters.get(i)
                ));
            }
            if (edgeParameters.isEmpty() && !vertexParameters.isEmpty()) {
                allVertices.computeIfAbsent(vertexParameters.get(0), Vertex<Integer, Integer>::new);
            }
        });
        return new Graph<>(new ArrayList<>(allVertices.values()), allEdges);
    }
}
