package com.camera.surfaceview.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import com.camera.surfaceview.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

public class BmpUtils {

	// RESAMPLING functions return bitmap sized to nearest power of 2
	// if you need exact size, please use RESIZING functions
	
	public static Bitmap getResampledBitmap(byte[] data, int reqSize) {
		return getResampledBitmap(data, reqSize, reqSize);
	}

	public static Bitmap getResampledBitmap(String pathName, int reqSize) {
		return getResampledBitmap(pathName, reqSize, reqSize);
	}
	
	public static Bitmap getResampledBitmap(ContentResolver contentResolver,
											Uri uri, int reqSize) {
		return getResampledBitmap(contentResolver, uri, reqSize, reqSize);
	}
	
	public static Bitmap getResampledBitmap(byte[] data, 
			int reqWidth, int reqHeight) {
		// First decode with inJustDecodeBounds=true to check dimensions
	    final BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = true;
	    BitmapFactory.decodeByteArray(data, 0, data.length, options);

	    // Calculate inSampleSize
	    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

	    // Decode bitmap with inSampleSize set
	    options.inJustDecodeBounds = false;
	    return BitmapFactory.decodeByteArray(data, 0, data.length, options);
	}

	public static Bitmap getResampledBitmap(String pathName,
	        int reqWidth, int reqHeight) {

	    // First decode with inJustDecodeBounds=true to check dimensions
	    final BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = true;
	    BitmapFactory.decodeFile(pathName, options);

	    // Calculate inSampleSize
	    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

	    // Decode bitmap with inSampleSize set
	    options.inJustDecodeBounds = false;
	    return BitmapFactory.decodeFile(pathName, options);
	}

	public static Bitmap getResampledBitmap(ContentResolver contentResolver,
			Uri uri, int reqWidth, int reqHeight) {

		// First decode with inJustDecodeBounds=true to check dimensions
	    final BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = true;
		InputStream stream = null;
		try {
			stream = contentResolver.openInputStream(uri);
			BitmapFactory.decodeStream(stream, null, options);
		} catch (FileNotFoundException e) {
			return null;
		} finally {
			try {
				if(stream != null)
					stream.close();
			} catch (IOException e) {
			}
		}

	    // Calculate inSampleSize
	    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

	    // Decode bitmap with inSampleSize set
	    options.inJustDecodeBounds = false;

		stream = null;
		try {
			stream = contentResolver.openInputStream(uri);
			return BitmapFactory.decodeStream(stream, null, options);
		} catch (FileNotFoundException e) {
			return null;
		} finally {
			try {
				if(stream != null)
					stream.close();
			} catch (IOException e) {
			}
		}
	}
	
	// This function is not working! Since stream cannot be rewinded.
	// Consider to use other variants
	@SuppressWarnings("unused")
	private static Bitmap getResampledBitmap(InputStream stream,
	        int reqWidth, int reqHeight) {
		throw new RuntimeException("sorry");
	}
	
	public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
	    // Raw height and width of image
	    final int height = options.outHeight;
	    final int width = options.outWidth;
	    int inSampleSize = 1;
	
	    if (height > reqHeight || width > reqWidth) {
	
	        // Calculate ratios of height and width to requested height and width
	        final int heightRatio = Math.round((float) height / (float) reqHeight);
	        final int widthRatio = Math.round((float) width / (float) reqWidth);
	
	        // Choose the smallest ratio as inSampleSize value, this will guarantee
	        // a final image with both dimensions larger than or equal to the
	        // requested height and width.
	        inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
	    }
	
	    return inSampleSize;
	}

	/* Use resampling function before #getResizedBitmap */
	public static Bitmap getResizedBitmap(Bitmap bm, int newHeight, int newWidth) {
		int width = bm.getWidth();
		int height = bm.getHeight();
		float scaleWidth = ((float) newWidth) / width;
		float scaleHeight = ((float) newHeight) / height;
		// CREATE A MATRIX FOR THE MANIPULATION
		Matrix matrix = new Matrix();
		// RESIZE THE BIT MAP
		matrix.postScale(scaleWidth, scaleHeight);

		// "RECREATE" THE NEW BITMAP
		System.gc();
		Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height,
				matrix, false);
		return resizedBitmap;
	}
	
	public static Bitmap getResizedBitmap(Bitmap bm, int newSize) {

		int width = bm.getWidth();
		int height = bm.getHeight();
		int newWidth, newHeight;

		if (width >= height) {
			newHeight = newSize;
			newWidth = (int) (width * ((float)newSize / height));
		} else {
			newWidth = newSize;
			newHeight = (int) (height * ((float) newSize / width));
		}

		float scaleWidth = ((float) newWidth) / width;
		float scaleHeight = ((float) newHeight) / height;
		// CREATE A MATRIX FOR THE MANIPULATION
		Matrix matrix = new Matrix();
		// RESIZE THE BIT MAP
		matrix.postScale(scaleWidth, scaleHeight);

		// "RECREATE" THE NEW BITMAP
		System.gc();
		Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height,
				matrix, false);
		return resizedBitmap;
	}
	

	public static Bitmap getResizedBitmap(byte[] data, int reqSize) {
		return getResizedBitmap( 
				getResampledBitmap(data, reqSize), 
				reqSize);
	}

	public static Bitmap getResizedBitmap(String pathName, int reqSize) {
		return getResizedBitmap(
				getResampledBitmap(pathName, reqSize, reqSize),
				reqSize, reqSize);
	}
	
	public static Bitmap getResizedBitmap(ContentResolver contentResolver,
			Uri uri, int reqSize) {
		return getResizedBitmap(
				getResampledBitmap(contentResolver, uri, reqSize),
				reqSize);
	}
	
	
	public static int getDrawableIdByName(String name) {
		if (name == null)
			return -1;

		try {
			Field field = R.drawable.class.getDeclaredField(name);
			int id = field.getInt(field);
			return id;
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

		return -1;
	}

	public static Bitmap cropBitmapToSquare(Bitmap bmp) {

		System.gc();
		Bitmap result = null;
		int height = bmp.getHeight();
		int width = bmp.getWidth();
		if (height <= width) {
			result = Bitmap.createBitmap(bmp, (width - height) / 2, 0, height,
					height);
		} else {
			result = Bitmap.createBitmap(bmp, 0, (height - width) / 2, width,
					width);
		}
		return result;
	}

	public static Bitmap cropBitmap(byte[] data) {
		Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
		return cropBitmapToSquare(bmp);
	}

	public static Bitmap cropBitmap(InputStream is) {
		return cropBitmapToSquare(BitmapFactory.decodeStream(is));
	}

	public static void rotateBitmap(String path, int degrees)
			throws IOException {
		System.gc();
		Bitmap bmp = BitmapFactory.decodeFile(path);
		Matrix matrix = new Matrix();
		matrix.postRotate(degrees);
		bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(),
				matrix, false);
		File file = new File(path);
		FileOutputStream outStream = new FileOutputStream(file);
		bmp.compress(Bitmap.CompressFormat.JPEG, 60, outStream);
		outStream.close();
	}

	public static Bitmap rotateBitmap(Bitmap photo, int rotation, 
			int reqHeight, int reqWidth) {
		Matrix matrix = new Matrix();
        matrix.preRotate(rotation);
        return Bitmap.createBitmap(photo, 0, 0, 
        		reqHeight, reqWidth, matrix, true);
  	}
}
