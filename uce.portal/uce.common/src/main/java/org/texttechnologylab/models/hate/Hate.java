package org.texttechnologylab.models.hate;

import org.texttechnologylab.annotations.Typesystem;
import org.texttechnologylab.models.UIMAAnnotation;
import org.texttechnologylab.models.WikiModel;
import org.texttechnologylab.models.corpus.Document;

import javax.persistence.*;

@Entity
@Table(name = "hate")
@Typesystem(types = {org.texttechnologylab.annotation.Hate.class})
public class Hate extends UIMAAnnotation implements WikiModel {
    @Column(name = "hate", nullable = false)
    private double hate;

    @Column (name = "non_hate", nullable = false)
    private double nonHate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    public Hate() {
        super(-1, -1);
    }

    public Hate(int begin, int end) {
        super(begin, end);
    }

    public Hate(int begin, int end, String coveredText) {
        super(begin, end);
        setCoveredText(coveredText);
    }

    public double getHate() {
        return hate;
    }

    public  void setHate(double hate) {
        this.hate = hate;
    }

    public double getNonHate() {
        return nonHate;
    }

    public void setNonHate(double nonHate) {
        this.nonHate = nonHate;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    @Override
    public String getWikiId() {
        return "UT" + "-" + this.getId();
    }

}
