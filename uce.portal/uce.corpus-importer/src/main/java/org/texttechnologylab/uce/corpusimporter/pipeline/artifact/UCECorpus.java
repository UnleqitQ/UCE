package org.texttechnologylab.uce.corpusimporter.pipeline.artifact;

import org.texttechnologylab.uce.common.config.CorpusConfig;
import org.texttechnologylab.uce.common.models.corpus.Corpus;

import java.nio.file.Path;
import java.util.Objects;

public final class UCECorpus {
    private final String importId;
    private final Path corpusRoot;
    private final Path inputRoot;
    private CorpusConfig corpusConfig;
    private Corpus corpus;

    public UCECorpus(String importId, Path corpusRoot) {
        this.importId = Objects.requireNonNull(importId, "importId");
        this.corpusRoot = Objects.requireNonNull(corpusRoot, "corpusRoot");
        this.inputRoot = corpusRoot.resolve("input");
    }

    public String importId() { return importId; }
    public Path corpusRoot() { return corpusRoot; }
    public Path inputRoot() { return inputRoot; }
    public CorpusConfig corpusConfig() { return corpusConfig; }
    public Corpus corpus() { return corpus; }

    public void corpusConfig(CorpusConfig corpusConfig) { this.corpusConfig = corpusConfig; }
    public void corpus(Corpus corpus) { this.corpus = corpus; }
}
