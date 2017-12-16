package com.github.fedorov_s_n.graphs

import com.github.fedorov_s_n.graphs.representation.TestGraphRepresentation
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

class GraphTest extends Specification {

    private final TestGraphRepresentation parser = new TestGraphRepresentation();

    @Unroll
    def "#method0(#input) -> (#output)"() {
        expect:
        assert parser.restore(input).map(map).(method0.trim())() == parser.restore(output)
        where:
        method0         | input                          | map                | output
        "invert"        | "1 -/2/-> 3->4"                | { it }             | "4->3 -/2/-> 1"
        "distinct"      | "1 -/6/-> 5->6, 0->4 -/7/-> 7" | { (int) (it / 2) } | "0->2->3, 0 -/6/-> 2 -/7/-> 3"
        "distinctEdges" | "1->2->2->1, 1->2"             | { it }             | "1->2->2->1"
    }

    @Unroll
    def "#method1(#input) -> (#output)"() {
        expect:
        assert parser.restore(input).(method1.trim())(argument) == parser.restore(output)
        where:
        method1       | input                 | argument                 | output
        "map"         | "1->2->3->4->2"       | { it * 3 }               | "3->6->9->12->6"
        "mapEdges"    | "1 -/2/-> 3 -/4/-> 5" | { it * 2 }               | "1 -/4/-> 3 -/8/-> 5"
        "remove"      | "1->2->3->4->2"       | 2                        | "1, 3->4"
        "remove"      | "1->2->2"             | 3                        | "1->2->2"
        "removeEdges" | "1->2 -/0/-> 3"       | 0                        | "1->2, 3"
        "removeEdges" | "1->2 -/0/-> 3"       | null                     | "1, 2 -/0/-> 3"
        "filter"      | "1->2->3->4->2"       | { it.parameter != 3 }    | "1->2, 4->2"
        "filter"      | "1->2->3->4->2"       | { it.parameter > 2 }     | "3->4"
        "filterEdges" | "1->2 -/0/-> 3"       | { it.parameter != null } | "1, 2 -/0/-> 3"
        "filterEdges" | "1->2 -/0/-> 3"       | { it.parameter == null } | "1->2, 3"
    }

    @Unroll
    def "#method3(#input) -> (#output)"() {
        expect:
        def input = "1 -/3/-> 2 -/2/-> 3 -> 4 -/4/-> 2" // ran out of space :(
        def arg1 = { it.parameter != 2 }
        def arg3 = { a, b -> a * b }
        assert parser.restore(input).(method3.trim())(arg1, arg2, arg3) == parser.restore(output)
        where:
        method3     | arg2                     | output
        "propagate" | { true }                 | "1 -/6/-> 3 -> 4 -/8/-> 3"
        "propagate" | { it.parameter > 7 }     | "1, 3 , 4 -/8/-> 3"
        "propagate" | { it.parameter == null } | "1, 3 -> 4"
    }


    @Unroll
    def "#split(#input) -> (#output)"() {
        expect:
        def actual = parser.restore(input).(split.trim())().sort { a, b -> a.size() <=> b.size() };
        assert actual == output.collect { parser.restore(it) }
        where:
        split        | input                      | output
        "split"      | "1->2,3-/5/->4->6"         | ["1->2", "3-/5/->4->6"]
        "split"      | "1"                        | ["1"]
        "splitEdges" | "1->2->3->4->2"            | ["1", "2->3->4->2"]
        "splitEdges" | "1->2->3->5->6->5,3->4->2" | ["1", "5->6->5", "2->3->4->2"]
    }

    @Unroll
    def "topsort(#input) -> (#output)"() {
        expect:
        def graph = parser.restore(input).topsort()
        if (output == null) {
            assert graph == null
        } else {
            assert graph != null
            assert graph.vertices().map({ it.parameter.toString() }).collect(Collectors.joining()) == output
        }
        where:
        input              | output
        "4->3->2->1"       | "4321"
        "1->2->1"          | null
        "1->2, 1->2, 1->2" | "12"
    }
}
