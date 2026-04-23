package de.robv.android.xposed.callbacks;

public abstract class XCallback {

    public int priority;

    public XCallback() {
        this(50);
    }

    public XCallback(int priority) {
        this.priority = priority;
    }

    public static class Param {
        public Object[] args;
    }
}
