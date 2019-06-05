package ca.uwaterloo.cs.streamingrpq.dfa;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by anilpacaci on 2019-01-31.
 */
public class DFANode {

    private List<DFANode> downstreamNodes;

    private Integer nodeId;

    private boolean isFinal;


    /**
     * generates an internal node with random id
     */
    public DFANode() {
        this(ThreadLocalRandom.current().nextInt(), false);
    }

    public DFANode(Integer nodeId) {
        this(nodeId, false);
    }

    public DFANode(Integer nodeId, boolean isFinal) {
        this.nodeId = nodeId;
        this.isFinal = isFinal;
        this.downstreamNodes = new ArrayList<>();
    }

    public Integer getNodeId() {
        return nodeId;
    }

    public void setNodeId(Integer nodeId) {
        this.nodeId = nodeId;
    }

    public void addDownstreamNode(DFANode downstreamNode) {
        this.downstreamNodes.add(downstreamNode);
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }
}
