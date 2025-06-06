package org.texttechnologylab.models.negation;

import org.texttechnologylab.models.UIMAAnnotation;
import org.texttechnologylab.models.corpus.Document;
import org.texttechnologylab.models.corpus.Page;

import javax.persistence.*;

/**
 * Scope consists of one span, ManyToOne relation to CompleteNegation (one negation can have multiple Scopes)
 */
@Entity
@Table(name="scope")
public class Scope extends UIMAAnnotation {
    @ManyToOne
    @JoinColumn(name = "negation_id")
    private CompleteNegation negation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    public Scope(){
        super(-1, -1);
    }
    public Scope(int begin, int end) {
        super(begin, end);
    }
    public Scope(int begin, int end, String coveredText){
        super(begin, end);
        setCoveredText(coveredText);
    }

    public CompleteNegation getNegation() {
        return negation;
    }

    public void setNegation(CompleteNegation negation) {
        this.negation = negation;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }
}
