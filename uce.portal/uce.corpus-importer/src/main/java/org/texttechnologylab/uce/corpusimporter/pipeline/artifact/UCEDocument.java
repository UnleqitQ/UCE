package org.texttechnologylab.uce.corpusimporter.pipeline.artifact;

import org.apache.uima.jcas.JCas;
import org.texttechnologylab.uce.common.models.corpus.Document;

import java.nio.file.Path;
import java.util.Objects;

public final class UCEDocument {
    private final String importId;
    private final long corpusId;
    private final Path sourcePath;
    private JCas originalCas;
    private JCas selectedCas;
    private Document document;
    private boolean duplicate;
    private Object domainGraphBuffer;

    public UCEDocument(String importId, long corpusId, Path sourcePath) {
        this.importId = Objects.requireNonNull(importId, "importId");
        this.corpusId = corpusId;
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
    }

    public String importId() { return importId; }
    public long corpusId() { return corpusId; }
    public Path sourcePath() { return sourcePath; }
    public JCas originalCas() { return originalCas; }
    public JCas selectedCas() { return selectedCas; }
    public Document document() { return document; }
    public boolean duplicate() { return duplicate; }
    public Object domainGraphBuffer() { return domainGraphBuffer; }

    public void originalCas(JCas originalCas) { this.originalCas = originalCas; }
    public void selectedCas(JCas selectedCas) { this.selectedCas = selectedCas; }
    public void document(Document document) { this.document = document; }
    public void duplicate(boolean duplicate) { this.duplicate = duplicate; }
    public void domainGraphBuffer(Object domainGraphBuffer) { this.domainGraphBuffer = domainGraphBuffer; }
}
