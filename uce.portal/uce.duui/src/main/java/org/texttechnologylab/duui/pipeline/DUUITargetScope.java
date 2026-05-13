package org.texttechnologylab.duui.pipeline;

public final class DUUITargetScope<T> implements AutoCloseable {
    private final DUUIFlowScope<T> parent;
    private final DUUITarget<T> target;
    private boolean closed;

    private DUUITargetScope(DUUIFlowScope<T> parent, DUUITarget<T> target) {
        this.parent = parent;
        this.target = target;
        if (!parent.artifactType().equals(target.inputType())) {
            throw new IllegalArgumentException("Target input type does not match parent flow.");
        }
        parent.addStage(DUUIStage.linear(target.inputType().id() + "-target", java.util.List.of(DUUIComponents.target(target))));
    }

    public static <T> DUUITargetScope<T> open(DUUIFlowScope<T> parent, DUUITarget<T> target) {
        return new DUUITargetScope<>(parent, target);
    }

    public DUUITarget<T> target() {
        return target;
    }

    @Override
    public void close() {
        closed = true;
    }
}
