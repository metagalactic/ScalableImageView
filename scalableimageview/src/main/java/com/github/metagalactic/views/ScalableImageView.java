package com.github.metagalactic.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ScalableImageView extends AppCompatImageView {

    public static final String TAG = ScalableImageView.class.getSimpleName();

    /**
     * The default value for the maximum scale factor
     */
    private static final float DEFAULT_SCALE_MAX = 3f;

    /**
     * The scale value for the default, unscaled state
     */
    private static final float NO_SCALE = 1f;

    /**
     * Indicates no pan or translation value
     */
    private static final float NO_TRANSLATION = 0f;

    /**
     * Used to clear the last pointer ID
     */
    private static final int NO_POINTER = -1;

    /**
     * Duration for the reset animation in milliseconds
     */
    private static final int RESET_ANIMATION_DURATION = 300;

    private boolean mIsAnimating = false;
    private boolean mIsMultiPointerEventSeries = false;
    private boolean mIsScalable = true;

    private float mAttemptedPanMax = NO_TRANSLATION;

    private float mAttemptedScaleMax = NO_SCALE;
    private float mAttemptedScaleMin = NO_SCALE;
    private float mCurrentScale = NO_SCALE;
    private float mCurrentScaleMax = NO_SCALE;
    private float mCurrentScaleMin = NO_SCALE;
    private float mMaxScale = DEFAULT_SCALE_MAX;

    private int mLastPointerId = NO_POINTER;
    private int mTouchSlop;

    private Matrix mMatrix = new Matrix();
    private PointF mCurrentTranslation = new PointF();
    private PointF mInitialCoordinates = new PointF();
    private PointF mPreviousCoordinates = new PointF();
    private ScaleGestureDetector mScaleGestureDetector;

    public ScalableImageView(Context context) {
        super(context);
        init();
    }

    public ScalableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScalableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!mIsScalable) {
            return super.onTouchEvent(event);
        }

        // Check for a scroll event
        mScaleGestureDetector.onTouchEvent(event);

        boolean requestAllowParentIntercept = false;
        boolean handled = false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsMultiPointerEventSeries = true;
                mInitialCoordinates.x = event.getX();
                mInitialCoordinates.y = event.getY();
                mPreviousCoordinates.x = event.getX();
                mPreviousCoordinates.y = event.getY();
                // Disable the parent from intercepting touches until we have more information about
                // the nature of the event. This is necessary when the view is embedded in one or
                // more scrollable parents.
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() > 1) {
                    // If at any point in this series of events we have more than one pointer, we'll
                    // update this flag to indicate that
                    // (a) we should not allow parent intercepts at any point during the series
                    // (b) we should treat any event as handled if was not otherwise handled to prevent strange "click" behavior.
                    mIsMultiPointerEventSeries = true;
                }

                if (mIsAnimating) {
                    // Just update the last position
                    mPreviousCoordinates.x = event.getX();
                    mPreviousCoordinates.y = event.getY();
                    break;
                }
                if (mScaleGestureDetector.isInProgress()) {
                    // If scaling is happening during this series of events, we can safely say we
                    // consumed the event.
                    handled = true;

                    // Reset the last pointer in case we start panning later
                    mLastPointerId = NO_POINTER;
                } else if (isScaled()) {
                    // We'll manually keep track of which pointer we're using. If we don't have a
                    // pointer set or it has changed, reset the ID and the last event values
                    if (mLastPointerId == NO_POINTER || mLastPointerId != event.getPointerId(0)) {
                        mLastPointerId = event.getPointerId(0);
                        mPreviousCoordinates.x = event.getX();
                        mPreviousCoordinates.y = event.getY();
                    }

                    // This will keep track of total pan attempts so that we can decide to consume
                    // the final cancel/up event even if no actual panning occurred.
                    PointF initialDiff = new PointF(event.getX() - mInitialCoordinates.x,
                            event.getY() - mInitialCoordinates.y);
                    mAttemptedPanMax = Math.max(initialDiff.length(), mAttemptedPanMax);

                    // Check the raw value (before possibly truncating) to see if the event should
                    // be considered handled
                    PointF diff = new PointF(event.getX() - mPreviousCoordinates.x,
                            event.getY() - mPreviousCoordinates.y);
                    handled = (Float.compare(diff.length(), mTouchSlop) > 0);
                    diff = getTranslationInBounds(diff, ScalableImageView.this, mMatrix);
                    mCurrentTranslation.x += diff.x;
                    mCurrentTranslation.y += diff.y;
                    if (Float.compare(diff.length(), NO_TRANSLATION) != 0) {
                        // We've panned. Update the matrix and consume the event.
                        mMatrix.postTranslate(diff.x, diff.y);
                        setImageMatrix(mMatrix);
                    }
                } else {
                    // Unless there is a second pointer (or we're below a threshold), indicate that
                    // we should now allow the parent to intercept events to allow for scrolling.
                    PointF diff = new PointF(event.getX() - mInitialCoordinates.x,
                            event.getY() - mInitialCoordinates.y);
                    if (!mIsMultiPointerEventSeries &&
                            Float.compare(diff.length(), mTouchSlop) > 0) {
                        requestAllowParentIntercept = true;
                    }
                }
                mPreviousCoordinates.x = event.getX();
                mPreviousCoordinates.y = event.getY();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mLastPointerId = NO_POINTER;
                //mIsMultiPointerEventSeries = false;
                if (hasScaled() || hasAttemptedPan() || hasAttemptedScale()) {
                    handled = true;
                }
                resetAllTransientStates();
                break;
        }

        // Notify the parent that it may now intercept events (if necessary). Note that we only
        // want to do this if we haven't manually handled the event to avoid a nasty bug in
        // DrawerLayout
        if (!handled && requestAllowParentIntercept) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }

        return handled || mIsMultiPointerEventSeries || super.onTouchEvent(event);
    }

    private float calculateNewScale(float oldScale, float newScale) {
        // For now minimum scale is fixed at 1f
        return calculateNewScale(oldScale, newScale, NO_SCALE, mMaxScale);
    }

    private float calculateNewScale(float oldScale, float newScale, float minScale, float maxScale) {
        return Math.max(minScale, Math.min(oldScale * newScale, maxScale));
    }

    private void completeScalingReset() {
        mMatrix.reset();
        mCurrentTranslation.x = NO_TRANSLATION;
        mCurrentTranslation.y = NO_TRANSLATION;
        mCurrentScale = NO_SCALE;

        if (!ScaleType.FIT_CENTER.equals(getScaleType())) {
            setScaleType(ScaleType.FIT_CENTER);
        }
    }

    public void init() {
        final Context context = getContext();
        mScaleGestureDetector = new ScaleGestureDetector(context, mScaleListener);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private boolean hasAttemptedPan() {
        return Float.compare(mAttemptedPanMax, mTouchSlop) > 0f;
    }

    private boolean hasAttemptedScale() {
        return Float.compare(mAttemptedScaleMax, NO_SCALE) != 0f ||
                Float.compare(mAttemptedScaleMin, NO_SCALE) != 0f;
    }

    private boolean hasScaled() {
        return Float.compare(mCurrentScaleMin, mCurrentScaleMax) != 0;
    }

    public boolean isScalable() {
        return mIsScalable;
    }

    public boolean isScaled() {
        return Float.compare(mCurrentScale, NO_SCALE) != 0;
    }

    private void resetAllTransientStates() {
        resetHasScaled();
        resetHasAttemptedPan();
        resetHasAttemptedScale();
    }

    private void resetHasAttemptedPan() {
        mAttemptedPanMax = NO_TRANSLATION;
    }

    private void resetHasAttemptedScale() {
        mAttemptedScaleMax = mAttemptedScaleMin = NO_SCALE;
    }

    private void resetHasScaled() {
        mCurrentScaleMin = mCurrentScaleMax = mCurrentScale;
    }

    public void resetScaling() {
        resetScaling(false);
    }

    public void resetScaling(final boolean animate) {
        if (mIsAnimating) {
            return;
        }

        if (!animate || mMatrix.isIdentity()) {
            completeScalingReset();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(mCurrentScale, NO_SCALE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            PointF lastTranslation = new PointF(mCurrentTranslation.x, mCurrentTranslation.y);

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (mMatrix.isIdentity()) {
                    Log.e(TAG, "Identity matrix while animating");
                    return;
                }

                // Reset matrix
                mMatrix.set(getFitCenterMatrix(ScalableImageView.this));

                // Update scale
                float scale = (float) animation.getAnimatedValue();
                float centerX = getX() + getWidth() * 0.5f;
                float centerY = getY() + getHeight() * 0.5f;
                mMatrix.postScale(scale, scale, centerX, centerY);

                // Update translation
                float fraction = animation.getAnimatedFraction();
                lastTranslation.x *= (1 - fraction);
                lastTranslation.y *= (1 - fraction);
                mMatrix.postTranslate(lastTranslation.x, lastTranslation.y);

                // Update matrix
                setImageMatrix(mMatrix);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimating = false;
                completeScalingReset();
            }
        });

        animator.setDuration(RESET_ANIMATION_DURATION);
        animator.start();
    }

    public void setMaximumScale(float scale) {
        mMaxScale = scale;
    }

    public void setScalable(boolean scalable) {
        mIsScalable = scalable;
    }

    private ScaleGestureDetector.SimpleOnScaleGestureListener mScaleListener =
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                PointF focus = new PointF();
                PointF lastFocus = new PointF();

                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    lastFocus.x = detector.getFocusX();
                    lastFocus.y = detector.getFocusY();
                    mCurrentScaleMax = mCurrentScale;
                    mCurrentScaleMin = mCurrentScale;
                    return true;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    if (mIsAnimating) {
                        return true;
                    }

                    focus.x = detector.getFocusX();
                    focus.y = detector.getFocusY();

                    float scale = detector.getScaleFactor();
                    mAttemptedScaleMax = Math.max(scale, mAttemptedScaleMax);
                    mAttemptedScaleMin = Math.min(scale, mAttemptedScaleMin);
                    mCurrentScale = calculateNewScale(mCurrentScale, scale);
                    mCurrentScaleMax = Math.max(mCurrentScale, mCurrentScaleMax);
                    mCurrentScaleMin = Math.min(mCurrentScale, mCurrentScaleMin);

                    if (Float.compare(mCurrentScale, NO_SCALE) > 0) {
                        if (!ScaleType.MATRIX.equals(getScaleType())) {
                            // Ensure we have the correct scale type
                            setScaleType(ScaleType.MATRIX);
                        }

                        // For now we will always begin with a matrix that simulates a "fitCenter"
                        // type of behavior; we can then apply cumulative transformations to this
                        // matrix in order to have greater control over the current values of
                        // scales, translations, etc.
                        mMatrix.set(getFitCenterMatrix(ScalableImageView.this));

                        // Scale the matrix to the current scale around the view center
                        mMatrix.postScale(
                                mCurrentScale,
                                mCurrentScale,
                                getX() + getWidth() * 0.5f,
                                getY() + getHeight() * 0.5f);

                        // Re-apply any translations from panning and also translate according to
                        // to any lateral motion while scaling.
                        mCurrentTranslation.x += focus.x - lastFocus.x;
                        mCurrentTranslation.y += focus.y - lastFocus.y;
                        mCurrentTranslation = getTranslationInBounds(mCurrentTranslation,
                                ScalableImageView.this, mMatrix);
                        mMatrix.postTranslate(mCurrentTranslation.x, mCurrentTranslation.y);

                        lastFocus.x = focus.x;
                        lastFocus.y = focus.y;
                        setImageMatrix(mMatrix);
                        return true;
                    } else {
                        resetScaling(hasScaled());
                        return false;
                    }
                }
            };

    //--------------------------------------------------------------------------------------------//
    //------------------------------------- STATIC FUNCTIONS -------------------------------------//
    //--------------------------------------------------------------------------------------------//

    /**
     * Calculates the translation factor necessary to center the view's drawable for the given
     * scale.
     *
     * @param imageView the view with a drawable to be centered
     * @param scale     a scale factor that would be applied to the drawable before centering
     * @return the translation factor
     */
    private static PointF getCenteringTranslationForScale(@Nullable ImageView imageView, float scale) {
        PointF point = new PointF();
        if (imageView == null || imageView.getDrawable() == null) {
            return point;
        }

        Drawable drawable = imageView.getDrawable();
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        int viewWidth = getViewWidthMinusPadding(imageView);
        int viewHeight = getViewHeightMinusPadding(imageView);
        if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return point;
        }

        // Translate drawable to center
        float xScaled = drawableWidth * scale;
        float yScaled = drawableHeight * scale;
        float xTranslation = (viewWidth - xScaled) / 2f + 0.5f;
        float yTranslation = (viewHeight - yScaled) / 2f + 0.5f;
        point.set(xTranslation, yTranslation);
        return point;
    }

    /**
     * Calculates the complete matrix that should be applied to a view's drawable to imitate a
     * FIT_CENTER scale type behavior, i.e. centering and scaling the drawable so that one dimension
     * completely fills the corresponding view dimension while the other dimension maintains the
     * correct aspect ratio while remaining inside the view.
     *
     * @param imageView the view with a drawable to be centered
     * @return the matrix that should be applied
     */
    private static Matrix getFitCenterMatrix(ImageView imageView) {
        Matrix matrix = new Matrix();

        // Amount to scale drawable to match each direction
        float fitCenterScaleFactor = getFitCenterScaleFactor(imageView);
        matrix.setScale(fitCenterScaleFactor, fitCenterScaleFactor);

        // Translate drawable to center
        PointF translationVector = getCenteringTranslationForScale(imageView, fitCenterScaleFactor);
        matrix.postTranslate(translationVector.x, translationVector.y);

        return matrix;
    }

    /**
     * Calculates the scale factor needed to scale a drawable to fill one view dimension while
     * the other drawable dimension maintains the  correct aspect ratio while remaining inside the
     * view.
     *
     * @param imageView the view with a drawable to be centered
     * @return the scale factor
     */
    private static float getFitCenterScaleFactor(@Nullable ImageView imageView) {
        if (imageView == null || imageView.getDrawable() == null) {
            return NO_SCALE;
        }

        Drawable drawable = imageView.getDrawable();
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        int viewWidth = getViewWidthMinusPadding(imageView);
        int viewHeight = getViewHeightMinusPadding(imageView);
        if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return NO_SCALE;
        }

        float xScaling = ((float) viewWidth) / drawableWidth;
        float yScaling = ((float) viewHeight) / drawableHeight;

        // Always just pick the smallest dimension
        return Math.min(xScaling, yScaling);
    }

    /**
     * Given a proposed translation, this will check to ensure that (a) it will not translate the
     * drawable if it has not scaled to larger than the view dimensions and (b) it will not
     * translate the drawable edges back inside the view bounds if it has scaled larger than the
     * view dimensions.
     *
     * @param translation the proposed translation
     * @param imageView   the view containing the drawable to translate
     * @param matrix      the matrix that is currently applied to the drawable
     * @return the proposed translation if satisfying all criteria, else a corrected version that
     * satisfies them.
     */
    private static PointF getTranslationInBounds(PointF translation,
                                                 @Nullable ImageView imageView,
                                                 @Nullable Matrix matrix) {
        if (imageView == null || imageView.getDrawable() == null || matrix == null) {
            return translation;
        }

        RectF drawableBounds = new RectF(imageView.getDrawable().copyBounds());
        RectF viewBounds = new RectF(
                imageView.getLeft(),
                imageView.getTop(),
                imageView.getRight(),
                imageView.getBottom());

        // Update the drawable bounds according to the current matrix
        matrix.mapRect(drawableBounds);

        PointF correctedTranslation = new PointF();
        if (drawableBounds.width() < viewBounds.width()) {
            // Don't allow additional translations if still within the view bounds
            correctedTranslation.x = 0;
        } else {
            // Don't translate more than the current spacing between the drawable and view edges
            if (translation.x > 0) {
                float leftGap = viewBounds.left - drawableBounds.left;
                correctedTranslation.x = translation.x < leftGap ? translation.x : leftGap;
            } else {
                float rightGap = viewBounds.right - drawableBounds.right;
                correctedTranslation.x = translation.x > rightGap ? translation.x : rightGap;
            }
        }

        if (drawableBounds.height() < viewBounds.height()) {
            // Don't allow additional translations if still within the view bounds
            correctedTranslation.y = 0;
        } else {
            // Don't translate more than the current spacing between the drawable and view edges
            if (translation.y > 0) {
                float topGap = viewBounds.top - drawableBounds.top;
                correctedTranslation.y = translation.y < topGap ? translation.y : topGap;
            } else {
                float bottomGap = viewBounds.bottom - drawableBounds.bottom;
                correctedTranslation.y = translation.y > bottomGap ? translation.y : bottomGap;
            }
        }

        return correctedTranslation;
    }

    private static int getViewWidthMinusPadding(@Nullable View view) {
        if (view == null) {
            return 0;
        }
        return view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
    }

    private static int getViewHeightMinusPadding(@Nullable View view) {
        if (view == null) {
            return 0;
        }
        return view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
    }
}
