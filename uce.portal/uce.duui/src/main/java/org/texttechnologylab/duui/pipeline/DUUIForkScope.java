package org.texttechnologylab.duui.pipeline;

public final class DUUIForkScope<P, C> extends DUUIAbstractFlowScope<C> {
    private final DUUIFlowScope<P> parent;
    private final DUUIFork<P, C> fork;

    private DUUIForkScope(DUUIFlowScope<P> parent, DUUIFork<P, C> fork) {
        super(parent.pipeline(), fork.outputType());
        this.parent = parent;
        this.fork = fork;
        if (!parent.artifactType().equals(fork.inputType())) {
            throw new IllegalArgumentException("Fork input type does not match parent flow.");
        }
        parent.addStage(DUUIStage.linear(fork.outputType().id() + "-fork", java.util.List.of(DUUIComponents.fork(fork))));
    }

    public static <P, C> DUUIForkScope<P, C> open(DUUIFlowScope<P> parent, DUUIFork<P, C> fork) {
        return new DUUIForkScope<>(parent, fork);
    }

    public DUUIFlowScope<P> parent() {
        return parent;
    }

    public DUUIFork<P, C> fork() {
        return fork;
    }
}
