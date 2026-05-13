package org.texttechnologylab.duui.pipeline;

public final class DUUIAdapterScope<A, B> extends DUUIAbstractFlowScope<B> {
    private final DUUIFlowScope<A> parent;
    private final DUUIAdapter<A, B> adapter;

    private DUUIAdapterScope(DUUIFlowScope<A> parent, DUUIAdapter<A, B> adapter) {
        super(parent.pipeline(), adapter.outputType());
        this.parent = parent;
        this.adapter = adapter;
        if (!parent.artifactType().equals(adapter.inputType())) {
            throw new IllegalArgumentException("Adapter input type does not match parent flow.");
        }
        parent.addStage(DUUIStage.linear(adapter.outputType().id() + "-adapter", java.util.List.of(DUUIComponents.adapter(adapter))));
    }

    public static <A, B> DUUIAdapterScope<A, B> open(DUUIFlowScope<A> parent, DUUIAdapter<A, B> adapter) {
        return new DUUIAdapterScope<>(parent, adapter);
    }

    public DUUIFlowScope<A> parent() {
        return parent;
    }

    public DUUIAdapter<A, B> adapter() {
        return adapter;
    }
}
