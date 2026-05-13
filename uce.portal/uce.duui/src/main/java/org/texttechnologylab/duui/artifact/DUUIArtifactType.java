package org.texttechnologylab.duui.artifact;

import java.util.Objects;

public final class DUUIArtifactType<T> {
    private final String id;

    private DUUIArtifactType(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static <T> DUUIArtifactType<T> of(String id) {
        return new DUUIArtifactType<>(id);
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DUUIArtifactType<?> that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
