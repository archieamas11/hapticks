package de.robv.android.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {

    public XC_MethodReplacement() {
        super();
    }

    public XC_MethodReplacement(int priority) {
        super(priority);
    }

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
        try {
            Object result = replaceHookedMethod(param);
            param.setResult(result);
        } catch (Throwable t) {
            param.setThrowable(t);
        }
    }

    @Override
    protected final void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    public static XC_MethodReplacement returnConstant(final Object value) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return value;
            }
        };
    }

    public static final XC_MethodReplacement DO_NOTHING = new XC_MethodReplacement(Integer.MAX_VALUE) {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) {
            return null;
        }
    };
}
