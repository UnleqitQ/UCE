package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.exception.DUUIFailurePolicy;
import org.texttechnologylab.duui.orchestration.DUUIDispatchPolicy;

import java.util.ArrayList;
import java.util.List;

public final class DUUIStageScope<T> implements AutoCloseable {
    private final DUUIFlowScope<T> flow;
    private final String id;
    private final DUUIStageType type;
    private final List<DUUIComponent<T>> components = new ArrayList<>();
    private DUUIDispatchPolicy dispatchPolicy;
    private DUUIFailurePolicy failurePolicy;
    private boolean closed;

    DUUIStageScope(DUUIFlowScope<T> flow, String id, DUUIStageType type) {
        this.flow = flow;
        this.id = id;
        this.type = type;
    }

    public DUUIStageScope<T> lambda(DUUILambda<T> lambda) {
        if (!flow.artifactType().equals(lambda.inputType())) {
            throw new IllegalArgumentException("Lambda input type does not match stage flow.");
        }
        components.add(lambda);
        return this;
    }

    public DUUIV1ComponentBuilder<T> v1(String id) {
        return new DUUIV1ComponentBuilder<>(this, id);
    }

    public DUUIUimaComponentBuilder<T> uima(String id) {
        return new DUUIUimaComponentBuilder<>(this, id);
    }

    DUUIStageScope<T> component(DUUIComponent<T> component) {
        components.add(component);
        return this;
    }

    public DUUIStageScope<T> dispatchPolicy(DUUIDispatchPolicy dispatchPolicy) {
        this.dispatchPolicy = dispatchPolicy;
        return this;
    }

    public DUUIStageScope<T> failurePolicy(DUUIFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
        return this;
    }

    @Override
    public void close() {
        if (closed) return;
        if (components.isEmpty()) {
            throw new IllegalStateException("Stage '" + id + "' has no components.");
        }
        DUUIDispatchPolicy policy = dispatchPolicy;
        if (policy == null && type == DUUIStageType.PARALLEL) {
            policy = DUUIDispatchPolicy.mixed();
        }
        if (policy == null) {
            policy = DUUIDispatchPolicy.INHERIT;
        }
        flow.addStage(new DUUIStage<>(id, id, type, components, id, policy, failurePolicy));
        closed = true;
    }
}
