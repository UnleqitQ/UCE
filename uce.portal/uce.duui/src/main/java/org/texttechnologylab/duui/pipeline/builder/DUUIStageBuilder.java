package org.texttechnologylab.duui.pipeline.builder;

import org.texttechnologylab.duui.exception.DUUIFailurePolicy;
import org.texttechnologylab.duui.orchestration.DUUIDispatchPolicy;
import org.texttechnologylab.duui.pipeline.DUUIComponent;
import org.texttechnologylab.duui.pipeline.DUUIStage;
import org.texttechnologylab.duui.pipeline.DUUIStageType;

import java.util.ArrayList;
import java.util.List;

public final class DUUIStageBuilder<T> {
    private final String id;
    private String name;
    private DUUIStageType type = DUUIStageType.LINEAR;
    private final List<DUUIComponent<T>> components = new ArrayList<>();
    private String componentId;
    private DUUIDispatchPolicy dispatchPolicy = DUUIDispatchPolicy.INHERIT;
    private DUUIFailurePolicy failurePolicy;

    private DUUIStageBuilder(String id) { this.id = id; }

    public static <T> DUUIStageBuilder<T> create(String id) { return new DUUIStageBuilder<>(id); }

    public DUUIStageBuilder<T> name(String name) { this.name = name; return this; }
    public DUUIStageBuilder<T> linear() { this.type = DUUIStageType.LINEAR; return this; }
    public DUUIStageBuilder<T> parallel() { this.type = DUUIStageType.PARALLEL; return this; }
    public DUUIStageBuilder<T> component(DUUIComponent<T> component) { components.add(component); return this; }
    public DUUIStageBuilder<T> componentId(String componentId) { this.componentId = componentId; return this; }
    public DUUIStageBuilder<T> dispatchPolicy(DUUIDispatchPolicy dispatchPolicy) { this.dispatchPolicy = dispatchPolicy; return this; }
    public DUUIStageBuilder<T> failurePolicy(DUUIFailurePolicy failurePolicy) { this.failurePolicy = failurePolicy; return this; }

    public DUUIStage<T> build() {
        return new DUUIStage<>(id, name, type, components, componentId, dispatchPolicy, failurePolicy);
    }
}
