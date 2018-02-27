/**
 * Copyright 2018 Alibaba Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.android.bindingx.plugin.weex;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.alibaba.android.bindingx.core.LogProxy;
import com.alibaba.android.bindingx.core.PlatformManager;
import com.alibaba.android.bindingx.core.internal.Utils;
import com.taobao.weex.ui.component.WXComponent;
import com.taobao.weex.ui.component.WXScroller;
import com.taobao.weex.ui.component.WXText;
import com.taobao.weex.ui.view.WXTextView;
import com.taobao.weex.ui.view.border.BorderDrawable;
import com.taobao.weex.utils.WXUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Description:
 *
 * Created by rowandjj(chuyi)<br/>
 */

final class WXViewUpdateService {
    private static final Map<String,IWXViewUpdater> sExpressionInvokerMap;
    private static final NOpInvoker EMPTY_INVOKER = new NOpInvoker();

    private static final String PERSPECTIVE = "perspective";
    private static final String TRANSFORM_ORIGIN = "transformOrigin";

    static {
        sExpressionInvokerMap = new HashMap<>();
        sExpressionInvokerMap.put("opacity",new OpacityInvoker());
        sExpressionInvokerMap.put("transform.translate",new TranslateInvoker());
        sExpressionInvokerMap.put("transform.translateX",new TranslateXInvoker());
        sExpressionInvokerMap.put("transform.translateY",new TranslateYInvoker());

        sExpressionInvokerMap.put("transform.scale",new ScaleInvoker());
        sExpressionInvokerMap.put("transform.scaleX",new ScaleXInvoker());
        sExpressionInvokerMap.put("transform.scaleY",new ScaleYInvoker());

        sExpressionInvokerMap.put("transform.rotate",new RotateInvoker());
        sExpressionInvokerMap.put("transform.rotateZ",new RotateInvoker());
        sExpressionInvokerMap.put("transform.rotateX",new RotateXInvoker());
        sExpressionInvokerMap.put("transform.rotateY",new RotateYInvoker());

        sExpressionInvokerMap.put("width",new WidthInvoker());
        sExpressionInvokerMap.put("height",new HeightInvoker());

        sExpressionInvokerMap.put("background-color",new BackgroundInvoker());
        sExpressionInvokerMap.put("color", new ColorInvoker());

        sExpressionInvokerMap.put("scroll.contentOffset", new ContentOffsetInvoker());
        sExpressionInvokerMap.put("scroll.contentOffsetX", new ContentOffsetXInvoker());
        sExpressionInvokerMap.put("scroll.contentOffsetY", new ContentOffsetYInvoker());

        sExpressionInvokerMap.put("border-top-left-radius", new BorderRadiusTopLeftUpdater());
        sExpressionInvokerMap.put("border-top-right-radius", new BorderRadiusTopRightUpdater());
        sExpressionInvokerMap.put("border-bottom-left-radius", new BorderRadiusBottomLeftUpdater());
        sExpressionInvokerMap.put("border-bottom-right-radius", new BorderRadiusBottomRightUpdater());

        sExpressionInvokerMap.put("border-radius", new BorderRadiusUpdater());
    }

    @NonNull
    static IWXViewUpdater findInvoker(@NonNull String prop) {
        IWXViewUpdater invoker = sExpressionInvokerMap.get(prop);
        if(invoker == null) {
            LogProxy.e("unknown property [" + prop + "]");
            return EMPTY_INVOKER;
        }
        return invoker;
    }

    private static final class NOpInvoker implements IWXViewUpdater {
        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            //oops
        }
    }

    private static void postRunnable(View target, Runnable runnable) {
        if(Build.VERSION.SDK_INT >= 16) {
            target.postOnAnimation(runnable);
        } else {
            target.post(runnable);
        }
    }

    //内容滚动
    private static final class ContentOffsetInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull final PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            final View scrollView = findScrollTarget(component);
            if(scrollView == null) {
                return;
            }
            if(cmd instanceof Double) {
                final double val = (double) cmd;
                postRunnable(scrollView, new Runnable() {
                    @Override
                    public void run() {
                        scrollView.setScrollX((int) getRealSize(val,translator));
                        scrollView.setScrollY((int) getRealSize(val,translator));
                    }
                });
            } else if(cmd instanceof ArrayList) {
                ArrayList<Object> l = (ArrayList<Object>) cmd;
                if(l.size() >= 2 && l.get(0) instanceof Double && l.get(1) instanceof Double) {
                    final double x = (double) l.get(0);
                    final double y = (double) l.get(1);
                    postRunnable(scrollView, new Runnable() {
                        @Override
                        public void run() {
                            scrollView.setScrollX((int) getRealSize(x,translator));
                            scrollView.setScrollY((int) getRealSize(y,translator));
                        }
                    });
                }

            }
        }
    }

    private static final class ContentOffsetXInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull final PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            final View scrollView = findScrollTarget(component);
            if(scrollView == null) {
                return;
            }
            if(!(cmd instanceof Double)) {
                return;
            }
            final double val = (double) cmd;
            postRunnable(scrollView, new Runnable() {
                @Override
                public void run() {
                    scrollView.setScrollX((int) getRealSize(val,translator));
                }
            });
        }
    }

    private static final class ContentOffsetYInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull final PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            final View scrollView = findScrollTarget(component);
            if(scrollView == null) {
                return;
            }
            final double val = (double) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    scrollView.setScrollY((int) getRealSize(val,translator));
                }
            });
        }
    }

    private static final class OpacityInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            double val = (double) cmd;
            final float alpha = (float) (val);
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    targetView.setAlpha(alpha);
                }
            });
        }
    }

    private static final class TranslateInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull final PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {

            if(!(cmd instanceof ArrayList)) {
                return;
            }

            ArrayList<Object> list = (ArrayList<Object>) cmd;
            if(list.size() >= 2 && list.get(0) instanceof Double && list.get(1) instanceof Double) {
                final double x1 = (double) list.get(0);
                final double y1 = (double) list.get(1);
                postRunnable(targetView, new Runnable() {
                    @Override
                    public void run() {
                        targetView.setTranslationX((float) getRealSize(x1,translator));
                        targetView.setTranslationY((float) getRealSize(y1,translator));
                    }
                });
            }
        }
    }

    private static final class TranslateXInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull final PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            final double d = (double) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    targetView.setTranslationX((float) getRealSize(d,translator));
                }
            });
        }
    }

    private static final class TranslateYInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull final PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            final double d = (double) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    targetView.setTranslationY((float) getRealSize(d,translator));
                }
            });
        }
    }

    private static final class ScaleInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull final Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull final Map<String,Object> config) {
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    int perspective = WXUtils.getInt(config.get(PERSPECTIVE));
                    perspective = Utils.normalizedPerspectiveValue(targetView.getContext(),perspective);

                    Pair<Float,Float> pivot = Utils.parseTransformOrigin(
                            WXUtils.getString(config.get(TRANSFORM_ORIGIN),null),targetView);

                    if(perspective != 0) {
                        targetView.setCameraDistance(perspective);
                    }
                    if(pivot != null) {
                        targetView.setPivotX(pivot.first);
                        targetView.setPivotY(pivot.second);
                    }

                    if(cmd instanceof Double) {
                        final double val = (double) cmd;
                        targetView.setScaleX((float) val);
                        targetView.setScaleY((float) val);
                    } else if(cmd instanceof ArrayList) {
                        ArrayList<Object> list = (ArrayList<Object>) cmd;
                        if(list.size() >= 2 && list.get(0) instanceof Double && list.get(1) instanceof Double) {
                            final double x = (double) list.get(0);
                            final double y = (double) list.get(1);
                            targetView.setScaleX((float) x);
                            targetView.setScaleY((float) y);
                        }

                    }
                }
            });
        }
    }

    private static final class ScaleXInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull final Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull final Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Pair<Float,Float> pivot = Utils.parseTransformOrigin(
                            WXUtils.getString(config.get(TRANSFORM_ORIGIN),null),targetView);

                    if(pivot != null) {
                        targetView.setPivotX(pivot.first);
                        targetView.setPivotY(pivot.second);
                    }

                    final double d3 = (double) cmd;
                    targetView.setScaleX((float) d3);
                }
            });
        }
    }

    private static final class ScaleYInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull final Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull final Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Pair<Float,Float> pivot = Utils.parseTransformOrigin(
                            WXUtils.getString(config.get(TRANSFORM_ORIGIN),null),targetView);

                    if(pivot != null) {
                        targetView.setPivotX(pivot.first);
                        targetView.setPivotY(pivot.second);
                    }

                    final double d = (double) cmd;
                    targetView.setScaleY((float) d);
                }
            });
        }
    }

    private static final class RotateInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull final Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull final Map<String,Object> config) {

            if(!(cmd instanceof Double)) {
                return;
            }

            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    int perspective = WXUtils.getInt(config.get(PERSPECTIVE));
                    perspective = Utils.normalizedPerspectiveValue(targetView.getContext(),perspective);

                    Pair<Float,Float> pivot = Utils.parseTransformOrigin(
                            WXUtils.getString(config.get(TRANSFORM_ORIGIN),null),targetView);

                    if(perspective != 0) {
                        targetView.setCameraDistance(perspective);
                    }
                    if(pivot != null) {
                        targetView.setPivotX(pivot.first);
                        targetView.setPivotY(pivot.second);
                    }

                    final double d = (double) cmd;
                    targetView.setRotation((float) d);
                }
            });
        }
    }

    private static final class RotateXInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull final Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull final Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    int perspective = WXUtils.getInt(config.get(PERSPECTIVE));
                    perspective = Utils.normalizedPerspectiveValue(targetView.getContext(),perspective);

                    Pair<Float,Float> pivot = Utils.parseTransformOrigin(
                            WXUtils.getString(config.get(TRANSFORM_ORIGIN),null),targetView);

                    if(perspective != 0) {
                        targetView.setCameraDistance(perspective);
                    }
                    if(pivot != null) {
                        targetView.setPivotX(pivot.first);
                        targetView.setPivotY(pivot.second);
                    }

                    final double d = (double) cmd;
                    targetView.setRotationX((float) d);
                }
            });
        }
    }


    private static final class RotateYInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull final Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull final Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    int perspective = WXUtils.getInt(config.get(PERSPECTIVE));
                    perspective = Utils.normalizedPerspectiveValue(targetView.getContext(),perspective);

                    Pair<Float,Float> pivot = Utils.parseTransformOrigin(
                            WXUtils.getString(config.get(TRANSFORM_ORIGIN),null),targetView);

                    if(perspective != 0) {
                        targetView.setCameraDistance(perspective);
                    }
                    if(pivot != null) {
                        targetView.setPivotX(pivot.first);
                        targetView.setPivotY(pivot.second);
                    }

                    final double d = (double) cmd;
                    targetView.setRotationY((float) d);
                }
            });
        }
    }


    private static final class WidthInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            double d = (double) cmd;
            final ViewGroup.LayoutParams params1 = targetView.getLayoutParams();
            params1.width = (int) getRealSize(d,translator);
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    targetView.setLayoutParams(params1);
                }
            });
        }
    }

    private static final class HeightInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            double d = (double) cmd;
            final ViewGroup.LayoutParams params2 = targetView.getLayoutParams();
            params2.height = (int) getRealSize(d,translator);
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    targetView.setLayoutParams(params2);
                }
            });
        }
    }

    private static final class BackgroundInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String,Object> config) {
            if(!(cmd instanceof Integer)) {
                return;
            }
            final int d = (int) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Drawable drawable = targetView.getBackground();
                    if(drawable == null) {
                        targetView.setBackgroundColor(d);
                    } else if(drawable instanceof BorderDrawable) {
                        BorderDrawable borderDrawable = (BorderDrawable) drawable;
                        borderDrawable.setColor(d);
                    } else if(drawable instanceof ColorDrawable) {
                        ColorDrawable colorDrawable = (ColorDrawable) drawable;
                        colorDrawable.setColor(d);
                    }
                }
            });
        }
    }

    private static final class ColorInvoker implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull final WXComponent component,
                           @NonNull final View targetView,
                           @NonNull final Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String, Object> config) {
            if(!(cmd instanceof Integer)) {
                return;
            }
            final int d = (int) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    if(targetView instanceof TextView) {
                        ((TextView) targetView).setTextColor(d);
                    } else if(component instanceof WXText && targetView instanceof WXTextView) {
                        Layout layout = ((WXTextView) targetView).getTextLayout();
                        if(layout != null) {
                            CharSequence sequence = layout.getText();
                            if(sequence != null && sequence instanceof SpannableString) {
                                /*kind of ugly*/
                                ForegroundColorSpan[] spans = ((SpannableString) sequence).getSpans(
                                        0,
                                        sequence.length(),
                                        ForegroundColorSpan.class);
                                if(spans != null && spans.length == 1) {/*仅处理纯色text*/
                                    ((SpannableString) sequence).removeSpan(spans[0]);
                                    ((SpannableString) sequence).setSpan(new ForegroundColorSpan(d),
                                            0,
                                            sequence.length(),
                                            Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

                                    targetView.invalidate();
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    private static final class BorderRadiusTopLeftUpdater implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String, Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            final double d = (double) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Drawable drawable = targetView.getBackground();
                    if(drawable != null && drawable instanceof BorderDrawable) {
                        BorderDrawable borderDrawable = (BorderDrawable) drawable;
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_TOP_LEFT_RADIUS, (float) d);
                    }
                }
            });
        }
    }

    private static final class BorderRadiusTopRightUpdater implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String, Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            final double d = (double) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Drawable drawable = targetView.getBackground();
                    if(drawable != null && drawable instanceof BorderDrawable) {
                        BorderDrawable borderDrawable = (BorderDrawable) drawable;
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_TOP_RIGHT_RADIUS, (float) d);
                    }
                }
            });
        }
    }

    private static final class BorderRadiusBottomLeftUpdater implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String, Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            final double d = (double) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Drawable drawable = targetView.getBackground();
                    if(drawable != null && drawable instanceof BorderDrawable) {
                        BorderDrawable borderDrawable = (BorderDrawable) drawable;
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_BOTTOM_LEFT_RADIUS, (float) d);
                    }
                }
            });
        }
    }

    private static final class BorderRadiusBottomRightUpdater implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String, Object> config) {
            if(!(cmd instanceof Double)) {
                return;
            }
            final double d = (double) cmd;
            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Drawable drawable = targetView.getBackground();
                    if(drawable != null && drawable instanceof BorderDrawable) {
                        BorderDrawable borderDrawable = (BorderDrawable) drawable;
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_BOTTOM_RIGHT_RADIUS, (float) d);
                    }
                }
            });
        }
    }

    private static final class BorderRadiusUpdater implements IWXViewUpdater {

        @Override
        public void invoke(@NonNull WXComponent component,
                           @NonNull final View targetView,
                           @NonNull Object cmd,
                           @NonNull PlatformManager.IDeviceResolutionTranslator translator,
                           @NonNull Map<String, Object> config) {

            if(!(cmd instanceof ArrayList)) {
                return;
            }
            final ArrayList<Object> l = (ArrayList<Object>) cmd;
            if(l.size() != 4) {
                return;
            }

            postRunnable(targetView, new Runnable() {
                @Override
                public void run() {
                    Drawable drawable = targetView.getBackground();
                    if(drawable != null && drawable instanceof BorderDrawable) {
                        double topLeft = 0,topRight = 0,bottomLeft = 0,bottomRight = 0;
                        if(l.get(0) instanceof Double) {
                            topLeft = (double) l.get(0);
                        }
                        if(l.get(1) instanceof Double) {
                            topRight = (double) l.get(1);
                        }
                        if(l.get(2) instanceof Double) {
                            bottomLeft = (double) l.get(2);
                        }
                        if(l.get(3) instanceof Double) {
                            bottomRight = (double) l.get(3);
                        }

                        BorderDrawable borderDrawable = (BorderDrawable) drawable;
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_TOP_LEFT_RADIUS, (float) topLeft);
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_TOP_RIGHT_RADIUS, (float) topRight);
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_BOTTOM_LEFT_RADIUS, (float) bottomLeft);
                        borderDrawable.setBorderRadius(BorderDrawable.BORDER_BOTTOM_RIGHT_RADIUS, (float) bottomRight);
                    }
                }
            });
        }
    }

    private static double getRealSize(double size,@NonNull PlatformManager.IDeviceResolutionTranslator translator) {
        return translator.webToNative(size);
    }

    @Nullable
    private static View findScrollTarget(@NonNull WXComponent component) {
        if(!(component instanceof WXScroller)) {
            LogProxy.e("scroll offset only support on Scroller Component");
            return null;
        }
        WXScroller scroller = (WXScroller) component;
        return scroller.getInnerView();
    }
}
