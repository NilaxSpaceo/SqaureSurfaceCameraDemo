package com.camera.surfaceview;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.camera.surfaceview.utils.BmpUtils;
import com.camera.surfaceview.utils.CaptureUtils;
import com.camera.surfaceview.view.CamPreview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class CamActivity extends Activity {

    private final int RC_GET_PICTURE = 301;

    private CamPreview mPreview;

    private RelativeLayout previewParent;
    private LinearLayout blackTop;
    private LinearLayout blackBottom;
    private ImageButton ibFlash;
    private ImageButton ibGrid;
    private ImageView ivGridLines;
    private LinearLayout llGallery;
    private TextView textCamera;
    private SeekBar sbZoom;


    private CaptureUtils captureUtils;

    private boolean flashEnabled = false;

    private int activeCamera = 0;

    private OrientationEventListener orientationListener;
    private int screenOrientation = 90;
    private int THRESHOLD = 30;
    public static boolean isClicked = false;
    private ImageButton ibFlipCamera;


    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide status-bar
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Hide title-bar, must be before setContentView
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_cam);
        InitControls();

    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    void InitControls() {

        previewParent = (RelativeLayout) findViewById(R.id.rlPreview);
        blackTop = (LinearLayout) findViewById(R.id.llBlackTop);
        blackBottom = (LinearLayout) findViewById(R.id.llBlackBottom);
        llGallery = (LinearLayout) findViewById(R.id.llGallery);

        ibFlash = (ImageButton) findViewById(R.id.ibFlash);
        ibGrid = (ImageButton) findViewById(R.id.ibGrid);
        ivGridLines = (ImageView) findViewById(R.id.ivGridLines);
        ibFlipCamera = (ImageButton) findViewById(R.id.ibFlipCamera);
        sbZoom = (SeekBar) findViewById(R.id.sbZoom);


        if (Camera.getNumberOfCameras() > 1) {
            ibFlipCamera.setVisibility(View.VISIBLE);
        } else {
            ibFlipCamera.setVisibility(View.GONE);
        }
        orientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
            public void onOrientationChanged(int orientation) {
                if (isOrientation(orientation, 0))
                    screenOrientation = 0;
                else if (isOrientation(orientation, 90))
                    screenOrientation = 90;
                else if (isOrientation(orientation, 180))
                    screenOrientation = 180;
                else if (isOrientation(orientation, 270))
                    screenOrientation = 270;


            }
        };
    }


    protected boolean isOrientation(int orientation, int degree) {
        return (degree - THRESHOLD <= orientation && orientation <= degree + THRESHOLD);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isClicked = false;
//        checkLocationEnabled();
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;


        orientationListener.enable();

        setupCamera(activeCamera);

        if (sbZoom.getVisibility() == View.VISIBLE) {
            sbZoom.setProgress(0);
        }

        showLatestPhoto();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        orientationListener.disable();
    }


    private void setupCamera(final int camera) {
        // Set the second argument by your choice.
        // Usually, 0 for back-facing camera, 1 for front-facing camera.
        // If the OS is pre-gingerbreak, this does not have any effect.
        try {
            mPreview = new CamPreview(this, camera, CamPreview.LayoutMode.NoBlank);// .FitToParent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_connect_to_camera, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LayoutParams previewLayoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        // Un-comment below lines to specify the size.

        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        int width = outMetrics.widthPixels;
        int height = outMetrics.heightPixels;

        previewLayoutParams.height = width;
        previewLayoutParams.width = width;

        // Un-comment below line to specify the position.
        mPreview.setCenterPosition(width / 2, height / 2);

        previewParent.addView(mPreview, 0, previewLayoutParams);

        // there is changes in calculations
        // camera preview image centered now to have actual image at center of
        // view
        int delta = height - width;
        int btHeight = 0;// blackTop.getHeight();
        int fix = delta - btHeight;
        int fix2 = 0;// fix / 4;

        FrameLayout.LayoutParams blackBottomParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, fix / 2 + fix2);
        blackBottomParams.gravity = Gravity.BOTTOM;
        blackBottom.setLayoutParams(blackBottomParams);

        FrameLayout.LayoutParams blackTopParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, fix / 2 - fix2);
        blackTopParams.gravity = Gravity.TOP;
        blackTop.setLayoutParams(blackTopParams);

        captureUtils = new CaptureUtils(mPreview.getCamera());
        if (captureUtils.isCameraFlashAvailable()) {
            captureUtils.toggleFlash(flashEnabled);
            ibFlash.setVisibility(View.VISIBLE);
            ibFlash.setImageResource(flashEnabled ? R.drawable.flash : R.drawable.flash_off);
        } else {
            ibFlash.setVisibility(View.GONE);
        }
        mPreview.setOnZoomCallback(new CamPreview.ZoomCallback() {
            @Override
            public void onZoomChanged(int progress) {
                sbZoom.setProgress(progress);
            }
        });
        sbZoom.setVisibility(captureUtils.hasAutofocus() ? View.VISIBLE : View.GONE);

        sbZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if (mPreview != null) {
                    mPreview.onProgressChanged(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                if (mPreview != null && seekBar != null) {
                    mPreview.onStopTrackingTouch(seekBar.getProgress());
                }
            }
        });
    }

    private void releaseCamera() {
        if (mPreview != null) {
            mPreview.stop();
            previewParent.removeView(mPreview); // This is necessary.
            mPreview = null;
        }
    }

    @SuppressLint("NewApi")
    public void flipClick(View view) {
        if (Build.VERSION.SDK_INT < 9)
            return;

        if (Camera.getNumberOfCameras() > 1) {

            activeCamera = activeCamera == 0 ? 1 : 0;
            releaseCamera();
            setupCamera(activeCamera);
        }
    }

    public void flashClick(View view) {
        if (!captureUtils.isCameraFlashAvailable())
            return;
        flashEnabled = !flashEnabled;
        captureUtils.toggleFlash(flashEnabled);
        ibFlash.setImageResource(flashEnabled ? R.drawable.flash : R.drawable.flash_off);
    }


    public void captureClick(View view) {
        try {
            if (!isClicked) {
                captureUtils.takeShot(jpegCallback);
                isClicked = true;
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void selectFromGallery(View view) {
        Intent iGetAvatar = new Intent(Intent.ACTION_PICK, Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(iGetAvatar, RC_GET_PICTURE);
    }


    PictureCallback jpegCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            FileOutputStream outStream = null;
            try {
                System.gc();
                Bitmap bmp = BmpUtils.getResampledBitmap(data, 800);
                bmp = BmpUtils.cropBitmapToSquare(bmp);

                // Write to file
//                File file = File.createTempFile("cam", "tmp", getFilesDir());
                // File file = new
                // File(Environment.getExternalStorageDirectory(), "cam.jpg");
//                outStream = new FileOutputStream(file);

                String FILENAME = "tmp" + System.currentTimeMillis() + ".jpg";

                outStream = openFileOutput(FILENAME, Context.MODE_PRIVATE);

                bmp.compress(Bitmap.CompressFormat.JPEG, 60, outStream);
                // outStream.write(data);
                outStream.close();
                resetCam();

                String path = getFilesDir() + "/" + FILENAME;
                int orientation = 0;
                int fix = 1;

                if (activeCamera == 0) {
                    ExifInterface ei = new ExifInterface(path);
                    orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    ei.setAttribute(ExifInterface.TAG_ORIENTATION, "90");
                    ei.saveAttributes();
                } else {
                    ExifInterface ei = new ExifInterface(path);
                    orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    ei.setAttribute(ExifInterface.TAG_ORIENTATION, "0");
                    ei.saveAttributes();
                    orientation = ExifInterface.ORIENTATION_ROTATE_270;
                    fix = -1;
                }

                switch (orientation) {
                    case ExifInterface.ORIENTATION_UNDEFINED:
                        BmpUtils.rotateBitmap(path, normalizeRot(90 + screenOrientation));
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        BmpUtils.rotateBitmap(path, normalizeRot(90 + screenOrientation));
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        BmpUtils.rotateBitmap(path, normalizeRot(180 + screenOrientation));
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        BmpUtils.rotateBitmap(path, normalizeRot(270 + fix * screenOrientation));
                        break;
                    case ExifInterface.ORIENTATION_NORMAL:
                        BmpUtils.rotateBitmap(path, normalizeRot(screenOrientation));
                        break;
                }

                selectCategory(path);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
        }
    };

    protected void selectCategory(String path) {
        Intent iSelect = new Intent(this, ShowPhotoActivity.class);
        iSelect.putExtra("path", path);
        startActivity(iSelect);
    }

    protected void resetCam() {
        Camera camera = mPreview.getCamera();
        camera.startPreview();
        showLatestPhoto();
    }

    protected int normalizeRot(int rot) {
        if (rot < 0)
            rot += 360;
        if (rot > 360)
            rot -= 360;
        return rot;
    }


    /**
     * Returns how much we have to rotate
     */
    public int rotationForImage(Uri uri) {
        try {
            if (uri.getScheme().equals("content")) {
                // From the media gallery
                String[] projection = {Images.ImageColumns.ORIENTATION};
                Cursor c = getContentResolver().query(uri, projection, null, null, null);
                if (c.moveToFirst()) {
                    return c.getInt(0);
                }
            } else if (uri.getScheme().equals("file")) {
                // From a file saved by the camera
                ExifInterface exif = new ExifInterface(uri.getPath());
                int rotation = (int) exifOrientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
                return rotation;
            }
            return 0;

        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get rotation in degrees
     */
    private static int exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    private void showLatestPhoto() {
        String[] projection = new String[]{Images.ImageColumns._ID, Images.ImageColumns.DATA,
                Images.ImageColumns.BUCKET_DISPLAY_NAME, Images.ImageColumns.DATE_TAKEN, Images.ImageColumns.MIME_TYPE};
        @SuppressWarnings("deprecation")
        final Cursor cursorE = managedQuery(Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, Images.ImageColumns.DATE_TAKEN
                + " DESC");

        @SuppressWarnings("deprecation")
        final Cursor cursorI = managedQuery(Images.Media.INTERNAL_CONTENT_URI, projection, null, null, Images.ImageColumns.DATE_TAKEN
                + " DESC");

        String imageLocation = null;
        long udate = 0;

        if (cursorE.moveToFirst()) {
            udate = cursorE.getLong(3);
            imageLocation = cursorE.getString(1);
        }

        if (cursorI.moveToFirst()) {
            long iudate = cursorI.getLong(3);
            if (iudate > udate)
                imageLocation = cursorI.getString(1);
        }

        if (imageLocation == null)
            return;

        final ImageView imageView = (ImageView) findViewById(R.id.ivGallery);
        int rot = rotationForImage(Uri.parse("file://" + imageLocation));
        File imageFile = new File(imageLocation);
        if (imageFile.exists()) {
            Bitmap bm = BmpUtils.getResampledBitmap(imageLocation, 100);
            if (rot != 0)
                bm = BmpUtils.rotateBitmap(bm, rot, 100, 100);
            imageView.setImageBitmap(bm);
        }
    }

    public void gridClick(View v) {
        boolean visible = ivGridLines.isShown();
        ivGridLines.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

}
