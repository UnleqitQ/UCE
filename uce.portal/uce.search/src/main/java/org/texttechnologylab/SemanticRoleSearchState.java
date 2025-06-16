package org.texttechnologylab;

import org.texttechnologylab.models.search.SearchType;

import java.util.ArrayList;
import java.util.List;

public class SemanticRoleSearchState extends SearchState{

    private List<String> arg0 = new ArrayList<>();
    private List<String> arg1 = new ArrayList<>();
    private List<String> arg2 = new ArrayList<>();
    private List<String> argm = new ArrayList<>();
    private String verb = "";

    public SemanticRoleSearchState(SearchType searchType) {
        super(searchType);
    }

    public List<String> getArg2() {
        return arg2;
    }

    public void setArg2(List<String> arg2) {
        this.arg2 = arg2;
    }

    public List<String> getArg0() {
        return arg0;
    }

    public void setArg0(List<String> arg0) {
        this.arg0 = arg0;
    }

    public List<String> getArg1() {
        return arg1;
    }

    public void setArg1(List<String> arg1) {
        this.arg1 = arg1;
    }

    public List<String> getArgm() {
        return argm;
    }

    public void setArgm(List<String> argm) {
        this.argm = argm;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }
}
