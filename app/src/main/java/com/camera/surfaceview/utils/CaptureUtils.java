package com.camera.surfaceview.utils;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;

import com.camera.surfaceview.CamActivity;

import java.util.List;

public class CaptureUtils {

	// //////////////////////////////////////////////////////////////////////
	// Fields
	// //////////////////////////////////////////////////////////////////////
	private Camera mCamera;

	private int currentOrientation = 90;

	int activeCamera = 0;

	// //////////////////////////////////////////////////////////////////////
	// Public methods
	// //////////////////////////////////////////////////////////////////////
	@SuppressWarnings("unused")
	private CaptureUtils() {
		initCamera();
	}

	public CaptureUtils(Camera camera) {
		mCamera = camera;
		
		Camera.Parameters params = mCamera.getParameters();
		List<String> focusList = params.getSupportedFocusModes();

		if (focusList.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		}

		mCamera.setParameters(params);
	}

	public void releaseCamera() {
		if (mCamera != null) {
			mCamera.release();
		}
	}

	public Camera getCamera() {
		return mCamera;
	}

	@SuppressLint("NewApi")
	public void flipCamera() {
		if (Build.VERSION.SDK_INT < 9)
			return;

		if (Camera.getNumberOfCameras() > 1) {
			activeCamera = activeCamera == 0 ? 1 : 0;
			initCamera();
		}
	}

	public void toggleFlash(boolean isEnabled) {
		if (!isCameraFlashAvailable())
			return;

		String mode;
		if (isEnabled)
			mode = Camera.Parameters.FLASH_MODE_ON;
		else
			mode = Camera.Parameters.FLASH_MODE_OFF;

		Camera.Parameters params = mCamera.getParameters();
		params.setFlashMode(mode);
		try {
			mCamera.setParameters(params);
		} catch (Exception e) {
			// bypass
		}
	}

	public boolean isCameraFlashAvailable() {
		Camera.Parameters p = mCamera.getParameters();
		return p.getFlashMode() == null ? false : true;
	}

	public boolean isCameraFlashEnabled() {
		Camera.Parameters p = mCamera.getParameters();
		String flashMode = p.getFlashMode();
		if (flashMode == null)
			return false;

		return flashMode.equals(Camera.Parameters.FLASH_MODE_ON);
	}

	public void takeShot(final Camera.PictureCallback callback) {
		final Camera.Parameters p = mCamera.getParameters();

		if (hasAutofocus()) {
			mCamera.autoFocus(new Camera.AutoFocusCallback() {

				@Override
				public void onAutoFocus(boolean success, Camera camera) {

                    Log.v(" takeShot", "onAutoFocus");

					try {
						camera.takePicture(null, null, callback);
						p.setRotation(180);
						// camera.setParameters(p);
					} catch (RuntimeException e) {
						// bypass
                        CamActivity.isClicked = false;
                        e.printStackTrace();
					}
				}
			});
		} else {
			try {
				// mCamera.setParameters(p);
				mCamera.takePicture(null, null, callback);
				p.setRotation(180);
			} catch (RuntimeException e) {
				// bypass
                e.printStackTrace();
            }
		}
	}

	public boolean hasAutofocus() {
		Camera.Parameters params = mCamera.getParameters();
		List<String> focusList = params.getSupportedFocusModes();
		return focusList.contains(Camera.Parameters.FOCUS_MODE_AUTO);
	}

	public PictureSize getPreviewSize(int reqHeight) {
		if (mCamera == null)
			return null;

		Camera.Parameters params = mCamera.getParameters();
		List<Camera.Size> sizes = params.getSupportedPreviewSizes();
		if (sizes.size() == 0)
			return null;

		Camera.Size size = sizes.get(0);
		float k = (float) reqHeight / size.height;
		int reqWidth = (int) (size.width * k);

		PictureSize reqSize = new PictureSize();
		reqSize.width = reqWidth;
		reqSize.height = reqHeight;

		params.setPreviewSize(reqWidth, reqHeight);

		return reqSize;
	}

	public static class PictureSize {
		public int width;
		public int height;
	}

	// //////////////////////////////////////////////////////////////////////
	// Private methods
	// //////////////////////////////////////////////////////////////////////

	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private void initCamera() {
		releaseCamera();

		if (Build.VERSION.SDK_INT < 9)
			mCamera = Camera.open();
		else
			mCamera = Camera.open(activeCamera);

		if (mCamera != null) {
			// mCamera.setDisplayOrientation(currentOrientation);
			toggleFlash(true);

		}
	}

}