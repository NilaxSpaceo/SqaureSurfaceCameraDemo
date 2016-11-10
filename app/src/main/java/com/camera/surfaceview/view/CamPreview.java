package com.camera.surfaceview.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

@SuppressLint("NewApi")
public class CamPreview extends SurfaceView implements SurfaceHolder.Callback, GestureDetector.OnGestureListener {
    private static boolean DEBUGGING = false;
    //    private static final String LOG_TAG = CamPreview.class.getSimpleName();
    private static final String CAMERA_PARAM_ORIENTATION = "orientation";
    private static final String CAMERA_PARAM_LANDSCAPE = "landscape";
    private static final String CAMERA_PARAM_PORTRAIT = "portrait";
    protected Activity mActivity;
    private SurfaceHolder mHolder;
    protected Camera mCamera;
    protected List<Camera.Size> mPreviewSizeList;
    protected List<Camera.Size> mPictureSizeList;
    protected Camera.Size mPreviewSize;
    protected Camera.Size mPictureSize;
    private int mSurfaceChangedCallDepth = 0;
    private int mCameraId;
    private LayoutMode mLayoutMode;
    private int mCenterPosX = -1;
    private int mCenterPosY;
    private float mDist = 0;
    PreviewReadyCallback mPreviewReadyCallback = null;
    private ZoomCallback zoomCallback = null;

    public static enum LayoutMode {
        FitToParent, // Scale to the size that no side is larger than the parent
        NoBlank // Scale to the size that no side is smaller than the parent
    }

    public interface PreviewReadyCallback {
        public void onPreviewReady();
    }

    public interface ZoomCallback {
        public void onZoomChanged(int progress);
    }

    /**
     * State flag: true when surface's layout size is set and surfaceChanged()
     * process has not been completed.
     */
    protected boolean mSurfaceConfiguring = false;

    public CamPreview(Activity activity, int cameraId, LayoutMode mode) {
        super(activity); // Always necessary
        mActivity = activity;
        mLayoutMode = mode;
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            if (Camera.getNumberOfCameras() > cameraId) {
                mCameraId = cameraId;
            } else {
                mCameraId = 0;
            }
        } else {
            mCameraId = 0;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            mCamera = Camera.open(mCameraId);
        } else {
            mCamera = Camera.open();
        }
        Camera.Parameters cameraParams = mCamera.getParameters();
        mPreviewSizeList = cameraParams.getSupportedPreviewSizes();
        mPictureSizeList = cameraParams.getSupportedPictureSizes();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Get the pointer ID

        Camera.Parameters params = mCamera.getParameters();

        if (params == null)
            return false;

        int action = event.getAction();


        if (event.getPointerCount() > 1) {
            // handle multi-touch events
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                Log.v("Camera","action == MotionEvent.ACTION_POINTER_DOWN");
                mDist = getFingerSpacing(event);
            } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {

                mCamera.cancelAutoFocus();
                handleZoom(event, params);
                Log.v("Camera", "action == MotionEvent.ACTION_MOVE && params.isZoomSupported()");
            }
        } else {
            // handle single touch events
            if (action == MotionEvent.ACTION_UP) {
                mCamera.cancelAutoFocus();
                handleFocus(event, params);
                Log.v("Camera","action == MotionEvent.ACTION_UP");
            }
        }
        return true;
    }

    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        if (newDist > mDist) {
            //zoom in
            if (zoom < maxZoom)
                zoom++;
        } else if (newDist < mDist) {
            //zoom out
            if (zoom > 0)
                zoom--;
        }
        mDist = newDist;
        params.setZoom(zoom);
        mCamera.setParameters(params);

        if (zoomCallback != null) {
            zoomCallback.onZoomChanged((zoom * 100 / maxZoom));
        }

        Log.e("Zoom", "MAX :" + maxZoom + "Zoom %" + (zoom * 100 / maxZoom));
    }

    public void handleFocus(MotionEvent event, Camera.Parameters params) {

        int pointerId = event.getPointerId(0);
        int pointerIndex = event.findPointerIndex(pointerId);
        // Get the pointer's current position
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            try {
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean b, Camera camera) {
                        // currently set to auto-focus on single touch
                    }
                });
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Determine the space between the first two fingers
     */
    private float getFingerSpacing(MotionEvent event) {
        // ...
        double x = event.getX(0) - event.getX(1);
        double y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(mHolder);
            Log.v("Camera","surfaceCreated");
        } catch (IOException e) {
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceChangedCallDepth++;
        doSurfaceChanged(width, height);
        mSurfaceChangedCallDepth--;
    }

    private void doSurfaceChanged(int width, int height) {
        mCamera.stopPreview();

        Camera.Parameters cameraParams = mCamera.getParameters();
        boolean portrait = isPortrait();

        // The code in this if-statement is prevented from executed again when
        // surfaceChanged is
        // called again due to the change of the layout size in this
        // if-statement.
        if (!mSurfaceConfiguring) {
            Camera.Size previewSize = determinePreviewSize(portrait, width, height);
            Camera.Size pictureSize = determinePictureSize(previewSize);

            mPreviewSize = previewSize;
            mPictureSize = pictureSize;
            mSurfaceConfiguring = adjustSurfaceLayoutSize(previewSize, portrait, width, height);
            // Continue executing this method if this method is called
            // recursively.
            // Recursive call of surfaceChanged is very special case, which is a
            // path from
            // the catch clause at the end of this method.
            // The later part of this method should be executed as well in the
            // recursive
            // invocation of this method, because the layout change made in this
            // recursive
            // call will not trigger another invocation of this method.
            if (mSurfaceConfiguring && (mSurfaceChangedCallDepth <= 1)) {
                return;
            }
        }

        configureCameraParameters(cameraParams, portrait);
        mSurfaceConfiguring = false;

        try {
            mCamera.startPreview();
        } catch (Exception e) {


            // Remove failed size
            mPreviewSizeList.remove(mPreviewSize);
            mPreviewSize = null;

            // Reconfigure
            if (mPreviewSizeList.size() > 0) { // prevent infinite loop
                surfaceChanged(null, 0, width, height);
            } else {
                Toast.makeText(mActivity, "Can't start preview", Toast.LENGTH_LONG).show();
            }
        }


        if (null != mPreviewReadyCallback) {
            mPreviewReadyCallback.onPreviewReady();
        }
    }

    /**
     * @param portrait
     * @param reqWidth  must be the value of the parameter passed in surfaceChanged
     * @param reqHeight must be the value of the parameter passed in surfaceChanged
     * @return Camera.Size object that is an element of the list returned from
     * Camera.Parameters.getSupportedPreviewSizes.
     */
    protected Camera.Size determinePreviewSize(boolean portrait, int reqWidth, int reqHeight) {
        // Meaning of width and height is switched for preview when portrait,
        // while it is the same as user's view for surface and metrics.
        // That is, width must always be larger than height for setPreviewSize.
        int reqPreviewWidth; // requested width in terms of camera hardware
        int reqPreviewHeight; // requested height in terms of camera hardware
        if (portrait) {
            reqPreviewWidth = reqHeight;
            reqPreviewHeight = reqWidth;
        } else {
            reqPreviewWidth = reqWidth;
            reqPreviewHeight = reqHeight;
        }



		/*
         * // Adjust surface size with the closest aspect-ratio float reqRatio =
		 * ((float) reqPreviewWidth) / reqPreviewHeight; float curRatio,
		 * deltaRatio; float deltaRatioMin = Float.MAX_VALUE; Camera.Size
		 * retSize = null; for (Camera.Size size : mPreviewSizeList) { curRatio
		 * = ((float) size.width) / size.height; deltaRatio = Math.abs(reqRatio
		 * - curRatio); if (deltaRatio < deltaRatioMin) { deltaRatioMin =
		 * deltaRatio; retSize = size; } }
		 */
        Display display = mActivity.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        Camera.Size retSize = null;
        int scrWidth = reqPreviewWidth;
        int found = scrWidth;

        for (Camera.Size size : mPreviewSizeList) {
            int diff = Math.abs(scrWidth - size.width);
            if (diff < found) {
                found = diff;
                retSize = size;
            }
        }

        if (retSize == null) {
            if (mPreviewSizeList != null && mPreviewSizeList.size() > 0) {
                retSize =mPreviewSizeList.get(0);
            }
        }

        return retSize;
    }

    protected Camera.Size determinePictureSize(Camera.Size previewSize) {

        Camera.Size retSize = null;
        for (Camera.Size size : mPictureSizeList) {
            if (size.equals(previewSize)) {
                return size;
            }
        }

        // if the preview size is not supported as a picture size
        float reqRatio = ((float) previewSize.width) / previewSize.height;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        for (Camera.Size size : mPictureSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }

        return retSize;
    }

    protected boolean adjustSurfaceLayoutSize(Camera.Size previewSize, boolean portrait,
                                              int availableWidth, int availableHeight) {
        float tmpLayoutHeight, tmpLayoutWidth;
        if (portrait) {
            tmpLayoutHeight = previewSize.width;
            tmpLayoutWidth = previewSize.height;
        } else {
            tmpLayoutHeight = previewSize.height;
            tmpLayoutWidth = previewSize.width;
        }

        float factH, factW, fact;
        factH = availableHeight / tmpLayoutHeight;
        factW = availableWidth / tmpLayoutWidth;
        if (mLayoutMode == LayoutMode.FitToParent) {
            // Select smaller factor, because the surface cannot be set to the
            // size larger than display metrics.
            if (factH < factW) {
                fact = factH;
            } else {
                fact = factW;
            }
        } else {
            if (factH < factW) {
                fact = factW;
            } else {
                fact = factH;
            }
        }

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.getLayoutParams();

        int layoutHeight = (int) (tmpLayoutHeight * fact);
        int layoutWidth = (int) (tmpLayoutWidth * fact);


        boolean layoutChanged;
        if ((layoutWidth != this.getWidth()) || (layoutHeight != this.getHeight())) {
            layoutParams.height = layoutHeight;
            layoutParams.width = layoutWidth;
            if (mCenterPosX >= 0) {
                layoutParams.topMargin = mCenterPosY - (layoutHeight / 2);
                layoutParams.leftMargin = mCenterPosX - (layoutWidth / 2);
            }
            this.setLayoutParams(layoutParams); // this will trigger another
            // surfaceChanged invocation.
            layoutChanged = true;
        } else {
            layoutChanged = false;
        }

        return layoutChanged;
    }

    /**
     * @param x X coordinate of center position on the screen. Set to negative
     *          value to unset.
     * @param y Y coordinate of center position on the screen.
     */
    public void setCenterPosition(int x, int y) {
        mCenterPosX = x;
        mCenterPosY = y;
    }

    protected void configureCameraParameters(Camera.Parameters cameraParams, boolean portrait) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) { // for 2.1 and
            // before
            if (portrait) {
                cameraParams.set(CAMERA_PARAM_ORIENTATION, CAMERA_PARAM_PORTRAIT);
            } else {
                cameraParams.set(CAMERA_PARAM_ORIENTATION, CAMERA_PARAM_LANDSCAPE);
            }
        } else { // for 2.2 and later
            int angle;
            Display display = mActivity.getWindowManager().getDefaultDisplay();
            switch (display.getRotation()) {
                case Surface.ROTATION_0: // This is display orientation
                    angle = 90; // This is camera orientation
                    break;
                case Surface.ROTATION_90:
                    angle = 270;
                    break;
                case Surface.ROTATION_180:
                    angle = 270;
                    break;
                case Surface.ROTATION_270:
                    angle = 90;
                    break;
                default:
                    angle = 90;
                    break;
            }

            mCamera.setDisplayOrientation(angle);
        }

        cameraParams.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        cameraParams.setPictureSize(mPictureSize.width, mPictureSize.height);


        mCamera.setParameters(cameraParams);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stop();
    }

    public void stop() {
        if (null == mCamera) {
            return;
        }
        mCamera.stopPreview();
        mCamera.release();

        mCamera = null;
    }

    public boolean isPortrait() {
        return (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
    }

    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        if (null == mCamera) {
            return;
        }
        mCamera.setOneShotPreviewCallback(callback);
    }

    public void setPreviewCallback(Camera.PreviewCallback callback) {
        if (null == mCamera) {
            return;
        }
        mCamera.setPreviewCallback(callback);
    }

    public Camera.Size getPreviewSize() {
        return mPreviewSize;
    }

    public void setOnPreviewReady(PreviewReadyCallback cb) {
        mPreviewReadyCallback = cb;
    }

    public void setOnZoomCallback(ZoomCallback zoomCallback) {
        this.zoomCallback = zoomCallback;
    }

    public Camera getCamera() {
        return mCamera;
    }


    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
//        Camera.Parameters parameters = camera.getParameters();

//        if(isZoomIn)
//            currentZoom += 1;
//        else
//            currentZoom -= 1;
//
//        if(currentZoom > parameters.getMaxZoom())
//        {
//            currentZoom = parameters.getMaxZoom();
//            isZoomIn = false;
//        }
//        else if(currentZoom <= 0)
//        {
//            currentZoom = 0;
//            isZoomIn = true;
//        }
//
//        parameters.setZoom(currentZoom);
//
//        camera.setParameters(parameters);
//
//        Log.i("CameraEffectsDemo", "Current Zoom: " + currentZoom + ", Max Zoom: " + parameters.getMaxZoom());

        return false;
    }

    public void onProgressChanged(int progress) {

        Camera camera = getCamera();

        if (camera != null && camera.getParameters() != null) {

            camera.cancelAutoFocus();

            Camera.Parameters params = mCamera.getParameters();
            int maxZoom = params.getMaxZoom();
            int zoom = (int) (maxZoom * ((float) progress / 100));
            params.setZoom(zoom);
            mCamera.setParameters(params);

        }
    }

    public void onStopTrackingTouch(int progress) {

        Camera camera = getCamera();

        if (camera != null && camera.getParameters() != null) {

            Camera.Parameters params = mCamera.getParameters();

            camera.cancelAutoFocus();

            //Check For AutoFocus
            List<String> supportedFocusModes = params.getSupportedFocusModes();
            if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean b, Camera camera) {
                        // currently set to auto-focus on single touch
                    }
                });
            }
        }

    }
}