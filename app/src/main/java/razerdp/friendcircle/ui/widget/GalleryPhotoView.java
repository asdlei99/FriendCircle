package razerdp.friendcircle.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.Scroller;

import com.socks.library.KLog;

import uk.co.senab.photoview.PhotoView;

/**
 * Created by 大灯泡 on 2016/12/20.
 * <p>
 * 用于图片浏览页面的photoview，主要负责展开动画和回退动画的计算
 * <p>
 * <p>
 * 本代码部分参考bm-x photoview(用scroller实现动画)：https://github.com/bm-x/PhotoView
 * 本代码大部分参考photoview的实现方式：https://github.com/chrisbanes/PhotoView
 */

public class GalleryPhotoView extends PhotoView {
    private static final int ANIMA_DURATION = 350;

    private ViewTransform viewTransfrom;
    private OnEnterAnimaEndListener onEnterAnimaEndListener;
    private OnExitAnimaEndListener onExitAnimaEndListener;

    private boolean isPlayingEnterAnima = false;
    private boolean isPlayingExitAnima = false;

    private Point globalOffset;
    private float[] scaleRatios;
    private RectF clipBounds;

    public GalleryPhotoView(Context context) {
        super(context);
    }

    public GalleryPhotoView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public GalleryPhotoView(Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);
    }

    @Override
    protected void init() {
        super.init();
        viewTransfrom = new ViewTransform();
        globalOffset = new Point();
    }

    @Override
    public void draw(Canvas canvas) {
        if (clipBounds != null) {
            canvas.clipRect(clipBounds);
            clipBounds = null;
        }
        super.draw(canvas);
    }

    public void playEnterAnima(final Rect from, @Nullable final OnEnterAnimaEndListener l) {
        this.onEnterAnimaEndListener = l;
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                playEnterAnimaInternal(from);
                getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
            }
        });
    }

    private void playEnterAnimaInternal(final Rect from) {
        if (isPlayingEnterAnima || from == null) return;

        final Rect tFrom = new Rect(from);
        final Rect to = new Rect();

        getGlobalVisibleRect(to, globalOffset);

        tFrom.offset(-globalOffset.x, -globalOffset.y);
        to.offset(-globalOffset.x, -globalOffset.y);

        scaleRatios = calculateRatios(tFrom, to);

        setPivotX(tFrom.centerX() / to.width());
        setPivotY(tFrom.centerY() / to.height());

        final AnimatorSet enterSet = new AnimatorSet();
        enterSet.play(ObjectAnimator.ofFloat(this, View.X, tFrom.left, to.left))
                .with(ObjectAnimator.ofFloat(this, View.Y, tFrom.top, to.top))
                .with(ObjectAnimator.ofFloat(this, View.SCALE_X, scaleRatios[0], 1f))
                .with(ObjectAnimator.ofFloat(this, View.SCALE_Y, scaleRatios[1], 1f));

        enterSet.setDuration(ANIMA_DURATION);
        enterSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                isPlayingEnterAnima = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isPlayingEnterAnima = false;
                if (onEnterAnimaEndListener != null) {
                    onEnterAnimaEndListener.onEnterAnimaEnd();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                isPlayingEnterAnima = false;

            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                isPlayingEnterAnima = true;

            }
        });
        enterSet.start();


    }


    public void playExitAnima(Rect to, @Nullable View alphaView, @Nullable final OnExitAnimaEndListener l) {
        this.onExitAnimaEndListener = l;
        playExitAnimaInternal(to, alphaView);
    }

    private void playExitAnimaInternal(final Rect to, @Nullable View alphaView) {
        if (isPlayingEnterAnima || to == null || mAttacher == null) return;

        final float scale = Math.max(scaleRatios[0], scaleRatios[1]);
        final float currentScale = getScale();
        if (currentScale > 1.0f) setScale(1.0f);

        final Rect from = getMineRect();
        final Rect target = new Rect(to);

        target.offset(-globalOffset.x, -globalOffset.y);

        viewTransfrom.animaTranslate(target.centerX(), from.centerX(), target.centerY(), from.centerY());
        //等比缩放
        viewTransfrom.animaScale(currentScale, scale, from.centerX(), from.centerY());
        if (alphaView != null) {
            viewTransfrom.animaAlpha(alphaView, 1.0f, 0);
        }

        if (target.width() < from.width() || target.height() < from.height()) {
            viewTransfrom.animaClip(from, target);
        }


        viewTransfrom.start(new OnAllFinishListener() {
            @Override
            public void onAllFinish() {
                if (onExitAnimaEndListener != null) {
                    onExitAnimaEndListener.onExitAnimaEnd();
                }
            }
        });


    }

    /**
     * 如果有drawable，则返回drawable相对于整个view的rect，否则返回整个view的rect
     *
     * @return
     */
    public Rect getMineRect() {
        Drawable drawable = getDrawable();
        Rect result = null;
        if (drawable != null) {
            result = getDrawableBounds(drawable);
        }
        if (result == null) {
            result = new Rect();
            result.set(0, 0, getWidth(), getHeight());
        }
        KLog.i(result.toShortString());
        return result;
    }

    private Rect rectF2rect(RectF rectf) {
        Rect rect = new Rect();
        if (rectf == null) {
            rect.setEmpty();
        } else {
            rect.left = (int) rectf.left;
            rect.top = (int) rectf.top;
            rect.right = (int) rectf.right;
            rect.bottom = (int) rectf.bottom;
        }
        return rect;
    }

    private Rect getDrawableBounds(Drawable d) {
        if (d == null) return null;
        Rect result = new Rect();
        Rect tDrawableRect = d.getBounds();
        Matrix drawableMatrix = getImageMatrix();

        float[] values = new float[9];
        drawableMatrix.getValues(values);

        result.left = (int) values[Matrix.MTRANS_X];
        result.top = (int) values[Matrix.MTRANS_Y];
        result.right = (int) (result.left + tDrawableRect.width() * values[Matrix.MSCALE_X]);
        result.bottom = (int) (result.top + tDrawableRect.height() * values[Matrix.MSCALE_Y]);

        return result;
    }


    private float[] calculateRatios(Rect startBounds, Rect endBounds) {
        float[] result = new float[2];
        float widthRatio = startBounds.width() * 1.0f / endBounds.width() * 1.0f;
        float heightRatio = startBounds.height() * 1.0f / endBounds.height() * 1.0f;
        result[0] = widthRatio;
        result[1] = heightRatio;
        return result;
    }


    public OnEnterAnimaEndListener getOnEnterAnimaEndListener() {
        return onEnterAnimaEndListener;
    }

    public void setOnEnterAnimaEndListener(OnEnterAnimaEndListener onEnterAnimaEndListener) {
        this.onEnterAnimaEndListener = onEnterAnimaEndListener;
    }

    public OnExitAnimaEndListener getOnExitAnimaEndListener() {
        return onExitAnimaEndListener;
    }

    public void setOnExitAnimaEndListener(OnExitAnimaEndListener onExitAnimaEndListener) {
        this.onExitAnimaEndListener = onExitAnimaEndListener;
    }

    public interface OnEnterAnimaEndListener {
        void onEnterAnimaEnd();
    }

    public interface OnExitAnimaEndListener {
        void onExitAnimaEnd();
    }

    interface OnAllFinishListener {
        void onAllFinish();
    }

    private class ViewTransform implements Runnable {

        static final float PRECISION = 10000f;

        View targetView;

        volatile boolean isRunning;

        Scroller translateScroller;
        Scroller scaleScroller;
        Scroller alphaScroller;
        Scroller clipScroller;

        Interpolator defaultInterpolator = new DecelerateInterpolator();


        int scaleCenterX;
        int scaleCenterY;

        float scaleX;
        float scaleY;

        float alpha;

        int dx;
        int dy;

        int preTranslateX;
        int preTranslateY;

        RectF mClipRect;
        RectF clipTo;
        RectF clipFrom;
        Matrix tempMatrix;

        OnAllFinishListener onAllFinishListener;


        ViewTransform() {
            isRunning = false;
            translateScroller = new Scroller(getContext(), defaultInterpolator);
            scaleScroller = new Scroller(getContext(), defaultInterpolator);
            alphaScroller = new Scroller(getContext(), defaultInterpolator);
            clipScroller = new Scroller(getContext(), defaultInterpolator);
            mClipRect = new RectF();
            tempMatrix = new Matrix();
        }

        void animaScale(float fromX, float toX, float fromY, float toY, int centerX, int centerY) {
            this.scaleCenterX = centerX;
            this.scaleCenterY = centerY;
            scaleScroller.startScroll((int) (fromX * PRECISION), (int) (fromY * PRECISION), (int) ((toX - fromX) * PRECISION), (int) ((toY - fromY) * PRECISION), ANIMA_DURATION);
        }

        void animaScale(float from, float to, int centerX, int centerY) {
            animaScale(from, to, from, to, centerX, centerY);
        }

        void animaTranslate(int fromX, int toX, int fromY, int toY) {
            preTranslateX = 0;
            preTranslateY = 0;
            translateScroller.startScroll(0, 0, fromX - toX, fromY - toY, ANIMA_DURATION);
        }

        void animaAlpha(View target, float fromAlpha, float toAlpha) {
            this.targetView = target;
            alphaScroller.startScroll((int) (fromAlpha * PRECISION), 0, (int) ((toAlpha - fromAlpha) * PRECISION), 0, ANIMA_DURATION);
        }

        void animaClip(Rect clipFrom, Rect clipTo) {
            this.clipFrom = new RectF(clipFrom);
            this.clipTo = new RectF(clipTo);
            if (!clipFrom.isEmpty() && !clipTo.isEmpty()) {
                //算出显示范围的比率
                float dx = Math.min(1.0f, (float) clipTo.width() / clipFrom.width());
                float dy = Math.min(1.0f, (float) clipTo.height() / clipFrom.height());

                dx = dx - 1;
                dy = dy - 1;
                //从1开始,乘以1w保证精度
                clipScroller.startScroll((int) (1 * PRECISION), (int) (1 * PRECISION), (int) (dx * PRECISION), (int) (dy * PRECISION), ANIMA_DURATION);
            }
        }

        @Override
        public void run() {

            boolean isAllFinish = true;

            if (scaleScroller.computeScrollOffset()) {
                scaleX = (float) scaleScroller.getCurrX() / PRECISION;
                scaleY = (float) scaleScroller.getCurrY() / PRECISION;

                isAllFinish = false;
            }

            if (translateScroller.computeScrollOffset()) {
                int curX = translateScroller.getCurrX();
                int curY = translateScroller.getCurrY();

                dx += curX - preTranslateX;
                dy += curY - preTranslateY;

                preTranslateX = curX;
                preTranslateY = curY;

                isAllFinish = false;
            }

            if (alphaScroller.computeScrollOffset()) {
                alpha = (float) alphaScroller.getCurrX() / PRECISION;
                isAllFinish = false;
            }

            if (clipScroller.computeScrollOffset() || clipBounds != null) {

                float curX = (float) clipScroller.getCurrX() / PRECISION;
                float curY = (float) clipScroller.getCurrY() / PRECISION;

                KLog.i("clip", curX, curY);


                if (curX == 1 && clipTo != null) {
                    mClipRect.left = clipTo.left;
                    mClipRect.right = clipTo.right;
                }

                if (curY == 1 && clipTo != null) {
                    mClipRect.top = clipTo.top;
                    mClipRect.bottom = clipTo.bottom;
                }

                if (!mClipRect.isEmpty()) {
                    clipBounds = mClipRect;
                }

            }

            if (!isAllFinish) {
                setMatrixValue();
                postExecuteSelf();
            } else {
                isRunning = false;
                reset();
                if (onAllFinishListener != null) {
                    onAllFinishListener.onAllFinish();
                }
            }

        }

        private void setMatrixValue() {
            if (mAttacher == null) return;
            resetSuppMatrix();
            postMatrixScale(scaleX, scaleY, scaleCenterX, scaleCenterY);
            postMatrixTranslate(dx, dy);
            if (targetView != null) targetView.setAlpha(alpha);
            applyMatrix();
        }

        private void postExecuteSelf() {
            if (isRunning) post(this);
        }

        private void reset() {
            scaleCenterX = 0;
            scaleCenterY = 0;

            scaleX = 0;
            scaleY = 0;

            dx = 0;
            dy = 0;

            preTranslateX = 0;
            preTranslateY = 0;

            alpha = 0;
        }

        void stop(boolean reset) {
            removeCallbacks(this);
            scaleScroller.abortAnimation();
            translateScroller.abortAnimation();
            alphaScroller.abortAnimation();
            clipScroller.abortAnimation();

            isRunning = false;
            onAllFinishListener = null;
            if (reset) reset();
        }

        void start(@Nullable OnAllFinishListener onAllFinishListener) {
            if (isRunning) stop(false);
            this.onAllFinishListener = onAllFinishListener;
            isRunning = true;
            postExecuteSelf();
        }
    }


    @Override
    protected void onDetachedFromWindow() {
        viewTransfrom.stop(true);
        super.onDetachedFromWindow();
    }


    //------------------------------------------tools block-----------------------------------------------
    private int getViewWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getViewHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    private int getDrawableIntrinsicWidth() {
        Drawable d = getDrawable();
        if (d == null) return 0;
        return d.getIntrinsicWidth();
    }

    private int getDrawableIntrinsicHeight() {
        Drawable d = getDrawable();
        if (d == null) return 0;
        return d.getIntrinsicHeight();
    }
}