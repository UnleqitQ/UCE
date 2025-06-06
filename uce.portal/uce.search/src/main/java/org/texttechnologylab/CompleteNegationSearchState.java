package org.texttechnologylab;

import org.texttechnologylab.models.search.SearchType;

import java.util.ArrayList;
import java.util.List;

public class CompleteNegationSearchState extends SearchState{

    private List<String> cue = new ArrayList<>();
    private List<String> scope = new ArrayList<>();
    private List<String> xscope = new ArrayList<>();
    private List<String> focus = new ArrayList<>();
    private List<String> event = new ArrayList<>();

    public CompleteNegationSearchState(SearchType searchType) {
        super(searchType);
    }


    public List<String> getCue() {
        return cue;
    }

    public void setCue(List<String> cue) {
        this.cue = cue;
    }

    public List<String> getScope() {
        return scope;
    }

    public void setScope(List<String> scope) {
        this.scope = scope;
    }

    public List<String> getXscope() {
        return xscope;
    }

    public void setXscope(List<String> xscope) {
        this.xscope = xscope;
    }

    public List<String> getFocus() {
        return focus;
    }

    public void setFocus(List<String> focus) {
        this.focus = focus;
    }

    public List<String> getEvent() {
        return event;
    }

    public void setEvent(List<String> event) {
        this.event = event;
    }
}
