package ca.uwaterloo.cs.streamingrpq.stree.engine;

import ca.uwaterloo.cs.streamingrpq.input.InputTuple;
import ca.uwaterloo.cs.streamingrpq.stree.data.*;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by anilpacaci on 2019-10-02.
 */
public class WindowedRAPQ<L> extends RPQEngine<L> {

    private long windowSize;
    private long slideSize;

    protected Histogram windowManagementHistogram;

    public WindowedRAPQ(QueryAutomata<L> query, int capacity, long windowSize, long slideSize) {
        super(query, capacity);
        this.windowSize = windowSize;
        this.slideSize = slideSize;
    }

    @Override
    public void addMetricRegistry(MetricRegistry metricRegistry) {
        windowManagementHistogram = metricRegistry.histogram("window-histogram");
        // call super function to include all other histograms
        super.addMetricRegistry(metricRegistry);
    }

    @Override
    public void processEdge(InputTuple<Integer, Integer, L> inputTuple) {
        Long windowStartTime = System.nanoTime();

        //for now window processing is done inside edge processing
        long currentTimetsamp = inputTuple.getTimestamp();
        if(currentTimetsamp >= windowSize && currentTimetsamp % slideSize == 0) {
            // its slide time, maintain the window
            expiry(currentTimetsamp - windowSize);
        }
        Long windowElapsedTime = System.nanoTime() - windowStartTime;

        // restart time for edge processing
        Long edgeStartTime = System.nanoTime();
        Timer.Context timer = fullTimer.time();
        // retrieve all transition that can be performed with this label
        Map<Integer, Integer> transitions = automata.getTransition(inputTuple.getLabel());

        if(transitions.isEmpty()) {
            // there is no transition with given label, simply return
            return;
        } else {
            // add edge to the snapshot graph
            graph.addEdge(inputTuple.getSource(), inputTuple.getTarget(), inputTuple.getLabel(), inputTuple.getTimestamp());
        }

        //create a spanning tree for the source node in case it does not exists
        if(transitions.keySet().contains(0) && !delta.exists(inputTuple.getSource())) {
            // if there exists a start transition with given label, there should be a spanning tree rooted at source vertex
            delta.addTree(inputTuple.getSource(), inputTuple.getTimestamp());
        }

        List<Map.Entry<Integer, Integer>> transitionList = transitions.entrySet().stream().collect(Collectors.toList());

        // for each transition that given label satisy
        for(Map.Entry<Integer, Integer> transition : transitionList) {
            int sourceState = transition.getKey();
            int targetState = transition.getValue();

            // iterate over spanning trees that include the source node
            for(SpanningTree spanningTree : delta.getTrees(inputTuple.getSource(), sourceState)) {
                // source is guarenteed to exists due to above loop,
                // we do not check target here as even if it exist, we might update its timetsap
                processTransition(spanningTree, inputTuple.getSource(), sourceState, inputTuple.getTarget(), targetState, inputTuple.getTimestamp());
            }
        }


        // metric recording
        Long edgeElapsedTime = System.nanoTime() - edgeStartTime;
        //populate histograms
        fullHistogram.update(edgeElapsedTime);
        timer.stop();
        // if the incoming edge is not discarded
        if(!transitions.isEmpty()) {
            // it implies that edge is processed
            processedHistogram.update(edgeElapsedTime);
        }
        if(currentTimetsamp % slideSize == 0) {
            windowManagementHistogram.update(windowElapsedTime);
        }
    }

    @Override
    public void processTransition(SpanningTree<Integer> tree, int parentVertex, int parentState, int childVertex, int childState, long edgeTimestamp) {
        TreeNode parentNode = tree.getNode(parentVertex, parentState);

        // either update timestamp, or create the node
        if(tree.exists(childVertex, childState)) {
            // if the child node already exists, we might need to update timestamp
            TreeNode childNode = tree.getNode(childVertex, childState);

            // root's children have timestamp equal to the edge timestamp
            // root timestmap always higher than any node in the tree
            if(parentNode.equals(tree.getRootNode())) {
                childNode.setTimestamp(edgeTimestamp);
                parentNode.setTimestamp( edgeTimestamp);
            }
            // child node cannot be the root because parent has to be at least
            else if(childNode.getTimestamp() < Long.min(parentNode.getTimestamp(), edgeTimestamp)) {
                // only update its timestamp if there is a younger  path, back edge is guarenteed to be at smaller or equal
                childNode.setTimestamp(Long.min(parentNode.getTimestamp(), edgeTimestamp));
                // properly update the parent pointer
                childNode.setParent(parentNode);
            }
        } else {
            // extend the spanning tree with incoming node

            // root's children have timestamp equal to the edge timestamp
            // root timestmap always higher than any node in the tree
            if(parentNode.equals(tree.getRootNode())) {
                tree.addNode(parentNode, childVertex, childState, edgeTimestamp);
                parentNode.setTimestamp(edgeTimestamp);
            }
            else {
                tree.addNode(parentNode, childVertex, childState, Long.min(parentNode.getTimestamp(), edgeTimestamp));
            }
            // add this pair to results if it is a final state
            if (automata.isFinalState(childState)) {
                results.put(tree.getRootVertex(), childVertex);
            }

            // get all the forward edges of the new extended node
            Collection<GraphEdge<Integer, L>> forwardEdges = graph.getForwardEdges(childVertex);

            if (forwardEdges == null) {
                // TODO better nul handling
                // end recursion if node has no forward edges
                return;
            } else {
                // there are forward edges, iterate over them
                for (GraphEdge<Integer, L> forwardEdge : forwardEdges) {
                    Integer targetState = automata.getTransition(childState, forwardEdge.getLabel());
                    // no need to check if the target node exists as we might need to update its timestamp even if it exists
                    if (targetState != null) {
                        // recursive call as the target of the forwardEdge has not been visited in state targetState before
                        processTransition(tree, childVertex, childState, forwardEdge.getTarget(), targetState, forwardEdge.getTimestamp());
                    }
                }
            }
        }
    }

    /**
     * updates Delta and Spanning Trees and removes any node that is lower than the window endpoint
     * might need to traverse the entire spanning tree to make sure that there does not exists an alternative path
     */
    private void expiry(long minTimestamp) {
        // first remove the expired edges from the graph
        graph.removeOldEdges(minTimestamp);
        // then maintain the spanning trees, not that spanning trees are maintained without knowing which edge is deleted
        delta.expiry(minTimestamp, graph, automata);
    }
}
