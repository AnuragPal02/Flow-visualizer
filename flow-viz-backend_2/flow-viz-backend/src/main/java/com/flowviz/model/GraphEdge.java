package com.flowviz.model;

public class GraphEdge {

    private String from;
    private String to;
    private int line;    // line in the caller method file where the call happens (0 = unknown)
    private String code; // that source line, trimmed

    public GraphEdge() {
    }

    public GraphEdge(String from, String to, int line, String code) {
        this.from = from;
        this.to = to;
        this.line = line;
        this.code = code;
    }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
