package org.texttechnologylab.models.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentSearchResult {

    private int documentCount;
    private List<Integer> documentIds;
    private List<Integer> documentHits;
    private Map<Integer, List<PageSnippet>> searchSnippets;
    private Map<Long, List<PageSnippet>> searchSnippetsDocIdToSnippet;
    private Map<Integer, Float> searchRanks;

    private List<AnnotationSearchResult> foundNamedEntities = new ArrayList<>();
    private List<AnnotationSearchResult> foundTimes = new ArrayList<>();
    private List<AnnotationSearchResult> foundTaxons = new ArrayList<>();

    private List<AnnotationSearchResult> foundScopes = new ArrayList<>();
    private List<AnnotationSearchResult> foundXscopes = new ArrayList<>();
    private List<AnnotationSearchResult> foundEvents = new ArrayList<>();
    private List<AnnotationSearchResult> foundFoci = new ArrayList<>();
    private List<AnnotationSearchResult> foundCues = new ArrayList<>();


    public DocumentSearchResult(int documentCount,
                                List<Integer> documentIds) {
        this.documentCount = documentCount;
        this.documentIds = documentIds;
    }

    public Map<Long, List<PageSnippet>> getSearchSnippetsDocIdToSnippet() {
        return searchSnippetsDocIdToSnippet;
    }

    public void setSearchSnippetsDocIdToSnippet(Map<Long, List<PageSnippet>> searchSnippetsDocIdToSnippet) {
        this.searchSnippetsDocIdToSnippet = searchSnippetsDocIdToSnippet;
    }

    public List<AnnotationSearchResult> getFoundScopes() {
        return foundScopes;
    }

    public void setFoundScopes(List<AnnotationSearchResult> foundScopes) {
        this.foundScopes = foundScopes;
    }

    public List<AnnotationSearchResult> getFoundXscopes() {
        return foundXscopes;
    }

    public void setFoundXscopes(List<AnnotationSearchResult> foundXscopes) {
        this.foundXscopes = foundXscopes;
    }

    public List<AnnotationSearchResult> getFoundEvents() {
        return foundEvents;
    }

    public void setFoundEvents(List<AnnotationSearchResult> foundEvents) {
        this.foundEvents = foundEvents;
    }

    public List<AnnotationSearchResult> getFoundFoci() {
        return foundFoci;
    }

    public void setFoundFoci(List<AnnotationSearchResult> foundFoci) {
        this.foundFoci = foundFoci;
    }

    public List<AnnotationSearchResult> getFoundCues() {
        return foundCues;
    }

    public void setFoundCues(List<AnnotationSearchResult> foundCues) {
        this.foundCues = foundCues;
    }

    public Map<Integer, Float> getSearchRanks() {
        return searchRanks;
    }

    public void setSearchRanks(Map<Integer, Float> searchRanks) {
        this.searchRanks = searchRanks;
    }

    public Map<Integer, List<PageSnippet>> getSearchSnippets() {
        return searchSnippets;
    }

    public void setSearchSnippets(Map<Integer, List<PageSnippet>> searchSnippets) {
        this.searchSnippets = searchSnippets;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public void setDocumentIds(List<Integer> documentIds) {
        this.documentIds = documentIds;
    }

    public List<Integer> getDocumentHits() {
        return documentHits;
    }

    public void setDocumentHits(List<Integer> documentHits) {
        this.documentHits = documentHits;
    }

    public void setFoundTimes(List<AnnotationSearchResult> foundTimes) {
        this.foundTimes = foundTimes;
    }

    public void setFoundTaxons(List<AnnotationSearchResult> foundTaxons) {
        this.foundTaxons = foundTaxons;
    }

    public List<AnnotationSearchResult> getFoundTimes() {
        return foundTimes;
    }

    public List<AnnotationSearchResult> getFoundTaxons() {
        return foundTaxons;
    }

    public List<AnnotationSearchResult> getFoundNamedEntities() {
        return foundNamedEntities;
    }

    public void setFoundNamedEntities(List<AnnotationSearchResult> foundNamedEntities) {
        this.foundNamedEntities = foundNamedEntities;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public List<Integer> getDocumentIds() {
        return documentIds;
    }

}
