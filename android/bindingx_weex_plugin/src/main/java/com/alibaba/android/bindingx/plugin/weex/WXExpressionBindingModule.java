package com.alibaba.android.bindingx.plugin.weex;

import android.support.annotation.Nullable;

import com.alibaba.android.bindingx.plugin.weex.internal.ExpressionPair;
import com.taobao.weex.WXSDKEngine;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;

import java.util.List;
import java.util.Map;

/**
 * Description:
 *
 * old binding APIs which will be removed later.
 * you should use {@link WXExpressionBindingV2Module} instead.
 *
 * Created by rowandjj(chuyi)<br/>
 */

@Deprecated
public final class WXExpressionBindingModule extends WXSDKEngine.DestroyableModule{

    private ExpressionBindingCore mExpressionBindingCore;

    @JSMethod
    @Deprecated
    public void enableBinding(@Nullable String sourceRef, @Nullable String eventType) {
        if(mExpressionBindingCore == null) {
            mExpressionBindingCore = new ExpressionBindingCore();
        }

        //空实现。 此方法仅为了与iOS兼容
    }

    @JSMethod
    @Deprecated
    public void createBinding(@Nullable String sourceRef, @Nullable String eventType, @Nullable String exitExpression,
                              @Nullable List<Map<String, Object>> expressionArgs, @Nullable final JSCallback callback) {
        if(mExpressionBindingCore == null) {
            mExpressionBindingCore = new ExpressionBindingCore();
        }

        ExpressionPair exitExpressionPair = ExpressionPair.create(null,exitExpression);
        mExpressionBindingCore.doBind(sourceRef, null, eventType, null, exitExpressionPair, expressionArgs, new ExpressionBindingCore.JavaScriptCallback() {
            @Override
            public void callback(Object params) {
                if(callback != null) {
                    callback.invokeAndKeepAlive(params);
                }
            }
        }, mWXSDKInstance);
    }

    @JSMethod
    @Deprecated
    public void disableBinding(@Nullable String sourceRef, @Nullable String eventType) {
        if(mExpressionBindingCore != null) {
            mExpressionBindingCore.doUnbind(sourceRef,eventType);
        }
    }

    @JSMethod
    @Deprecated
    public void disableAll() {
        if(mExpressionBindingCore != null) {
            mExpressionBindingCore.doRelease();
        }
    }

    @Override
    public void destroy() {
        if(mExpressionBindingCore != null) {
            mExpressionBindingCore.doRelease();
            mExpressionBindingCore = null;
        }
    }

    ///////// Lifecycle Callbacks

    @Override
    public void onActivityPause() {
        if(mExpressionBindingCore != null) {
            mExpressionBindingCore.onActivityPause();
        }
    }

    @Override
    public void onActivityResume() {
        if(mExpressionBindingCore != null) {
            mExpressionBindingCore.onActivityResume();
        }
    }

}
