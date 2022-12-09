package com.kpi.fict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class Node {
    public Token type;
    public String value;
    public List<Node> children = new ArrayList<>();

    public Node(Token type) {
        this.type = type;
    }

    public Node(Token type, String value) {
        this.type = type;
        this.value = value;
    }

    public Node getChild() {
        if (children.isEmpty()) {
            throw new RuntimeException("There are no children for node " + this);
        }

        return children.get(0);
    }

    public List<Node> getChildren() {
        return children.stream().filter(Objects::nonNull).toList();
    }

    public void setType(Token type) {
        this.type = type;
    }

    public void addChild(Node child) {
        children.add(child);
    }

    public void addChildren(Collection<Node> children) {
        this.children.addAll(children);
    }

    @Override
    public String toString() {
        if (value != null) {
            return "" + type + "(" + value + ")";
        }

        return "" + type;
    }
}
