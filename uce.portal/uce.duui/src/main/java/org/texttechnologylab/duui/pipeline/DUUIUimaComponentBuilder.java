package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifact;

public final class DUUIUimaComponentBuilder<T> {
    private final DUUIStageScope<T> stage;
    private final String id;
    private Object analysisEngineDescription;
    private String sourceView;
    private String targetView;
    private int scale = 1;
    private boolean registered;

    DUUIUimaComponentBuilder(DUUIStageScope<T> stage, String id) {
        this.stage = stage;
        this.id = id;
    }

    public DUUIUimaComponentBuilder<T> analysisEngine(Object analysisEngineDescription) {
        this.analysisEngineDescription = analysisEngineDescription;
        register();
        return this;
    }

    public DUUIUimaComponentBuilder<T> sourceView(String sourceView) {
        this.sourceView = sourceView;
        return this;
    }

    public DUUIUimaComponentBuilder<T> targetView(String targetView) {
        this.targetView = targetView;
        return this;
    }

    public DUUIUimaComponentBuilder<T> scale(int scale) {
        this.scale = Math.max(1, scale);
        return this;
    }

    private void register() {
        if (registered) return;
        stage.component(this::process);
        registered = true;
    }

    private DUUIArtifact<T> process(DUUIArtifact<T> artifact) {
        throw new UnsupportedOperationException("DUUI UIMA component execution is not bound in uce.duui yet: " + id);
    }

    public String id() { return id; }
    public Object analysisEngineDescription() { return analysisEngineDescription; }
    public String sourceView() { return sourceView; }
    public String targetView() { return targetView; }
    public int scale() { return scale; }
}
