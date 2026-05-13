package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifact;

public final class DUUIV1ComponentBuilder<T> {
    private final DUUIStageScope<T> stage;
    private final String id;
    private String endpoint;
    private String image;
    private String sourceView;
    private String targetView;
    private int scale = 1;
    private boolean registered;

    DUUIV1ComponentBuilder(DUUIStageScope<T> stage, String id) {
        this.stage = stage;
        this.id = id;
    }

    public DUUIV1ComponentBuilder<T> remote() {
        return this;
    }

    public DUUIV1ComponentBuilder<T> docker() {
        return this;
    }

    public DUUIV1ComponentBuilder<T> endpoint(String endpoint) {
        this.endpoint = endpoint;
        register();
        return this;
    }

    public DUUIV1ComponentBuilder<T> image(String image) {
        this.image = image;
        register();
        return this;
    }

    public DUUIV1ComponentBuilder<T> sourceView(String sourceView) {
        this.sourceView = sourceView;
        return this;
    }

    public DUUIV1ComponentBuilder<T> targetView(String targetView) {
        this.targetView = targetView;
        return this;
    }

    public DUUIV1ComponentBuilder<T> scale(int scale) {
        this.scale = Math.max(1, scale);
        return this;
    }

    private void register() {
        if (registered) return;
        stage.component(this::process);
        registered = true;
    }

    private DUUIArtifact<T> process(DUUIArtifact<T> artifact) {
        throw new UnsupportedOperationException("DUUI v1 component execution is not bound in uce.duui yet: " + id);
    }

    public String id() { return id; }
    public String endpoint() { return endpoint; }
    public String image() { return image; }
    public String sourceView() { return sourceView; }
    public String targetView() { return targetView; }
    public int scale() { return scale; }
}
