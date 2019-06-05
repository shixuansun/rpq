package ca.uwaterloo.cs.streamingrpq.dfa;

import ca.uwaterloo.cs.streamingrpq.data.*;
import ca.uwaterloo.cs.streamingrpq.input.InputTuple;
import com.google.common.collect.HashMultimap;

import java.util.*;

/**
 * Created by anilpacaci on 2019-02-22.
 */
public class DFA<L> extends DFANode {

    public static final int EXPECTED_NODES = 20000000;
    public static final int EXPECTED_NEIGHBOURS = 12;


    private HashMultimap<L, DFAEdge<L>> dfaEdegs = HashMultimap.create();
    private HashMap<Integer, DFANode> dfaNodes = new HashMap<>();
    private HashSet<RSPQTuple> results = new HashSet<>();

    // algoithm specific data structures
    private DFST delta = new DFST(2*EXPECTED_NODES, EXPECTED_NEIGHBOURS);
    private GraphEdges<ProductNode> edges = new GraphEdges<>(EXPECTED_NODES, EXPECTED_NEIGHBOURS);
    private Markings<ProductNode, RSPQTuple> markings = new Markings<>(EXPECTED_NODES, EXPECTED_NEIGHBOURS);
    private boolean[][] containmentMark;


    private Set<Integer> finalState = new HashSet<>();
    private Integer startState;

    public void addDFAEdge(Integer source, Integer target, L label) {
        DFANode sourceNode = dfaNodes.get(source);
        DFANode targetNode = dfaNodes.get(target);
        if(sourceNode == null) {
            sourceNode = new DFANode(source);
            dfaNodes.put(source, sourceNode);
        }
        if(targetNode == null) {
            targetNode = new DFANode(target);
            dfaNodes.put(target, targetNode);
        }

        sourceNode.addDownstreamNode(targetNode);
        dfaEdegs.put(label, new DFAEdge<>(sourceNode, targetNode, label));
    }

    public void setStartState(Integer startState) {
        this.startState = startState;
    }

    public void setFinalState(Integer finalState) {
        this.finalState.add(finalState);
        dfaNodes.get(finalState).setFinal(true);
        dfaNodes.get(finalState).addDownstreamNode(this);
    }

    public void processEdge(InputTuple<Integer, Integer, L> input) {
        Queue<QueuePair<RSPQTuple, ProductNode>> queue = new LinkedList<>();

        Set<DFAEdge<L>> dfaEdges = dfaEdegs.get(input.getLabel());

        for(DFAEdge<L> edge : dfaEdges) {
            // for each such node, add raw edge to the edges
            ProductNode sourceNode = new ProductNode(input.getSource(), edge.getSource().getNodeId());
            ProductNode targetNode = new ProductNode(input.getTarget(), edge.getTarget().getNodeId());

            // update set of existing edges

            edges.addNeighbour(sourceNode, targetNode);

            // if source state is 0 -> create a single edge tuple and add it to the queue
            if(edge.getSource().getNodeId() == this.startState) {
                RSPQTuple tuple = new RSPQTuple(input.getSource(), targetNode, null);
                queue.offer(new QueuePair<RSPQTuple, ProductNode>(tuple, sourceNode));
            }

            // query Delta to get all existing tuples that can be extended
            Collection<RSPQTuple> prefixes = delta.retrieveByTarget(sourceNode);
            for(RSPQTuple source : prefixes) {
                // extend the prefix path with the new edge
                RSPQTuple candidate = new RSPQTuple(source.getSource(), targetNode, source);
                queue.offer(new QueuePair<RSPQTuple, ProductNode>(candidate, sourceNode));
            }

        }


        while (!queue.isEmpty()) {
            QueuePair<RSPQTuple, ProductNode> candidate = queue.poll();
            RSPQTuple candidateTuple = candidate.getTuple();
            ProductNode predecessor = candidate.getProductNode();

            if (!delta.contains(candidateTuple)) {
                if(candidateTuple.getTargetState() == finalState) {
                    // new result
                    results.add(candidateTuple);
                }

                delta.addTuple(candidateTuple);

                Collection<ProductNode> extensionEdges = edges.getNeighbours(candidateTuple.getTargetNode());

                for(ProductNode extensionEdgeTarget : extensionEdges) {
                    // extend the newly added tuple with an existing edge
                    RSPQTuple tuple = new RSPQTuple(candidateTuple.getSource(), extensionEdgeTarget);
                    queue.offer(new QueuePair(tuple, candidateTuple.getTargetNode()));
                }
            }

        }

    }

    private void ExtendPrefixPath(RSPQTuple prefixPath, ProductNode node) {

        if(prefixPath.containsCM(node.getVertex(), node.getState())) {
            // return as this is clearly a cycle
        } else if( !hasContainment(prefixPath.getFirstCM(node.getVertex()), node.getState()) ) {
            // TODO: no containment, UNMARK
        } else if (markings.contains(node)) {
            // target node is marked, so extend it
            this.markings.addCrossEdge(node, prefixPath);
        } else {
            // TODO path is indeed extended and DFST is populated
        }
    }

    private boolean hasContainment(Integer stateQ, Integer stateT) {
        if(stateQ == null) {
            return true;
        }
        return !this.containmentMark[stateQ][stateT];
    }

    /**
     * Optimization procedure for the autamaton, including minimization, containment relationship
     * MUST be called after automaton is constructed
     */
    public void optimize() {
        this.containmentMark = new boolean[dfaNodes.size()][dfaNodes.size()];
        int alphabetSize = dfaEdegs.keySet().size();

        // once we construct the minimized DFA, we can easily compute the sufflix language containment relationship
        // Algorithm S of Wood'95
        Map<StatePair, List<StatePair>> stateLists = new HashMap<>();
        for(int s = 0; s < dfaNodes.size(); s++) {
            for (int t = 0; t < dfaNodes.size(); t++) {
                stateLists.put(StatePair.createInstance(s,t), new ArrayList<>());
            }
        }
        // first create a transition matrix for the DFA
        int[][] transitionMatrix = new int[dfaNodes.size()][alphabetSize];
        for(int i = 0; i < dfaNodes.size(); i++) {
            for(int j = 0; j < alphabetSize; j++) {
                transitionMatrix[i][j] = -1;
            }
        }
        Iterator<L> edgeIterator = dfaEdegs.keySet().iterator();
        for(int j = 0 ; j < alphabetSize; j++) {
            Set<DFAEdge<L>> edges = dfaEdegs.get(edgeIterator.next());
            for (DFAEdge<L> edge : edges) {
                transitionMatrix[edge.getSource().getNodeId()][j] = edge.getTarget().getNodeId();
            }
        }

        // initialize: line 1 of Algorithm S
        for(int s = 0; s < dfaNodes.size(); s++) {
            for (int t = 0; t < dfaNodes.size(); t++) {
                // for s \in S-F and t \in F
                if(!finalState.contains(s) && finalState.contains(t)) {
                    containmentMark[s][t] = true;
                }
            }
        }

        // line 2-7 of Algorithm S0
        for(int s = 0; s < dfaNodes.size(); s++) {
            for (int t = 0; t < dfaNodes.size(); t++) {
                // for s,t \in ((SxS) - ((S-F)xF))
                if(finalState.contains(s) || !finalState.contains(t)) {
                    // implement line 3,
                    boolean isMarked = false;
                    Queue<StatePair> markQueue = new ArrayDeque<>();
                    for(int j = 0; j < alphabetSize; j++) {
                        if(transitionMatrix[s][j] == transitionMatrix[t][j] && transitionMatrix[s][j] != -1) {
                            isMarked = true;
                            markQueue.add(StatePair.createInstance(s,t));
                        }
                    }

                    // recursively mark all the pairs on the list of pairs that are marked in this step
                    // line 5 of the Algorithm S
                    while(!markQueue.isEmpty()) {
                        StatePair pair = markQueue.poll();
                        List<StatePair> pairList = stateLists.get(pair);
                        for(StatePair candidate : pairList) {
                            if(!containmentMark[candidate.stateS][candidate.stateT]) {
                                markQueue.add(candidate);
                                containmentMark[candidate.stateS][candidate.stateT] = true;
                            }
                        }
                    }

                    // if there is no marked, then populate the lists
                    // line 6 of Algorithm S
                    if(!isMarked) {
                        for(int j = 0; j < alphabetSize; j++) {
                            int sEndpoint = transitionMatrix[s][j];
                            int tEndpoint = transitionMatrix[t][j];
                            if(sEndpoint != -1 && tEndpoint != -1 && sEndpoint != tEndpoint) {
                                // Line 7 of Algorithm S
                                stateLists.get(StatePair.createInstance(sEndpoint, tEndpoint)).add(StatePair.createInstance(s,t));
                            }
                        }
                    }

                }
            }
        }

    }

    // TODO: implementations of InsertRAPQ and DeleteRAPQ

    public int getResultCounter() {
        return results.size();
    }

    public Collection<RSPQTuple> getResults() {
        return results;
    }

    public int getGraphEdgeCount() {
        return edges.getEdgeCount();
    }

    public int getDeltaTupleCount() {
        return delta.getTupleCount();
    }
}
