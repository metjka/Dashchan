/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.ui.gallery;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Executor;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadFileTask;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.DecoderDrawable;
import com.mishiranu.dashchan.graphics.SimpleBitmapDrawable;
import com.mishiranu.dashchan.media.AnimatedPngDecoder;
import com.mishiranu.dashchan.media.GifDecoder;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.widget.PhotoView;

public class ImageUnit
{
	private final PagerInstance mInstance;
	
	private ReadFileTask mReadFileTask;
	private ReadBitmapCallback mReadBitmapCallback;
	
	public ImageUnit(PagerInstance instance)
	{
		mInstance = instance;
	}
	
	public void interrupt(boolean force)
	{
		if (force && mReadFileTask != null)
		{
			mReadFileTask.cancel();
			mReadFileTask = null;
			mReadBitmapCallback = null;
		}
		interruptHolder(mInstance.leftHolder);
		interruptHolder(mInstance.currentHolder);
		interruptHolder(mInstance.rightHolder);
	}
	
	private void interruptHolder(PagerInstance.ViewHolder holder)
	{
		if (holder != null)
		{
			if (holder.decodeBitmapTask != null)
			{
				((DecodeBitmapTask) holder.decodeBitmapTask).cancel(holder);
				holder.decodeBitmapTask = null;
			}
		}
	}
	
	private static final Executor EXECUTOR = ConcurrentUtils.newSingleThreadPool(20000, "DecodeBitmapTask", null, 0);
	
	public void applyImageFromFile(File file)
	{
		PagerInstance.ViewHolder holder = mInstance.currentHolder;
		GalleryItem galleryItem = holder.galleryItem;
		FileHolder fileHolder = FileHolder.obtain(file);
		if (!(holder.photoView.hasImage() && file.equals(holder.photoViewFile)))
		{
			if (holder.decodeBitmapTask != null) ((DecodeBitmapTask) holder.decodeBitmapTask).cancel(holder);
			DecodeBitmapTask decodeBitmapTask = new DecodeBitmapTask(file, fileHolder);
			decodeBitmapTask.executeOnExecutor(EXECUTOR);
			holder.decodeBitmapTask = decodeBitmapTask;
		}
		else
		{
			holder.fullLoaded = true;
			mInstance.galleryInstance.callback.invalidateOptionsMenu();
		}
		if (galleryItem.size <= 0)
		{
			galleryItem.size = (int) file.length();
			mInstance.galleryInstance.callback.updateTitle();
		}
		PagerInstance.ViewHolder nextHolder = mInstance.scrollingLeft ? mInstance.leftHolder : mInstance.rightHolder;
		if (nextHolder != null && Preferences.isLoadNearestImage())
		{
			GalleryItem nextGalleryItem = nextHolder.galleryItem;
			if (nextGalleryItem.isImage(mInstance.galleryInstance.locator))
			{
				Uri nextUri = nextGalleryItem.getFileUri(mInstance.galleryInstance.locator);
				File nextCachedFile = CacheManager.getInstance().getMediaFile(nextUri, true);
				if (nextCachedFile != null && !nextCachedFile.exists()) loadImage(nextUri, nextCachedFile, nextHolder);
			}
		}
	}
	
	public void applyImageLoad(Uri uri, File cachedFile)
	{
		loadImage(uri, cachedFile, mInstance.currentHolder);
	}
	
	private void loadImage(Uri uri, File cachedFile, PagerInstance.ViewHolder holder)
	{
		if (mReadBitmapCallback != null)
		{
			if (mReadBitmapCallback.isHolder(holder))
			{
				mReadBitmapCallback.attachDownloading();
				return;
			}
			mReadFileTask.cancel();
		}
		mReadBitmapCallback = new ReadBitmapCallback(holder);
		mReadFileTask = new ReadFileTask(mInstance.galleryInstance.context, mInstance.galleryInstance.chanName,
				uri, cachedFile, true, mReadBitmapCallback);
		mReadFileTask.executeOnExecutor(ReadFileTask.THREAD_POOL_EXECUTOR);
	}
	
	private class ReadBitmapCallback implements ReadFileTask.Callback, ReadFileTask.CancelCallback
	{
		private PagerInstance.ViewHolder mHolder;
		private final GalleryItem mGalleryItem;
		
		public ReadBitmapCallback(PagerInstance.ViewHolder holder)
		{
			mHolder = holder;
			mGalleryItem = holder.galleryItem;
		}
		
		private boolean isCurrentHolder()
		{
			return isHolder(mInstance.currentHolder);
		}
		
		public boolean isHolder(PagerInstance.ViewHolder holder)
		{
			return holder != null && holder.galleryItem == mGalleryItem;
		}
		
		@Override
		public void onFileExists(Uri uri, File file)
		{
			if (isCurrentHolder()) applyImageFromFile(file);
		}
		
		@Override
		public void onStartDownloading(Uri uri, File file)
		{
			if (isCurrentHolder())
			{
				mHolder.progressBar.setVisible(true, false);
				mHolder.progressBar.setIndeterminate(true);
			}
		}

		private int mPendingProgress;
		private int mPendingProgressMax;
		
		public void attachDownloading()
		{
			if (isCurrentHolder())
			{
				mHolder.progressBar.setVisible(true, false);
				mHolder.progressBar.setIndeterminate(mPendingProgressMax <= 0);
				if (mPendingProgressMax > 0)
				{
					mHolder.progressBar.setProgress(mPendingProgress, mPendingProgressMax, true);
				}
			}
		}
		
		@Override
		public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem)
		{
			mReadFileTask = null;
			mReadBitmapCallback = null;
			if (isCurrentHolder())
			{
				mHolder.progressBar.setVisible(false, false);
				if (success) applyImageFromFile(file); else mInstance.callback.showError(mHolder, errorItem.toString());
			}
		}
		
		@Override
		public void onCancelDownloading(Uri uri, File file)
		{
			if (isHolder(mHolder)) mHolder.progressBar.setVisible(false, true);
		}
		
		@Override
		public void onUpdateProgress(long progress, long progressMax)
		{
			if (isCurrentHolder())
			{
				mHolder.progressBar.setIndeterminate(false);
				mHolder.progressBar.setProgress((int) progress, (int) progressMax, progress == 0);
			}
			else
			{
				mPendingProgress = (int) progress;
				mPendingProgressMax = (int) progressMax;
			}
		}
	}
	
	private static final byte[] MAGIC_GIF_1 = {'G', 'I', 'F', '8', '7', 'a'};
	private static final byte[] MAGIC_GIF_2 = {'G', 'I', 'F', '8', '9', 'a'};
	private static final byte[] MAGIC_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
	
	private class DecodeBitmapTask extends AsyncTask<Void, Void, Void>
	{
		private final File mFile;
		private final FileHolder mFileHolder;
		
		private Bitmap mBitmap;
		private DecoderDrawable mDecoderDrawable;
		private AnimatedPngDecoder mAnimatedPngDecoder;
		private GifDecoder mGifDecoder;
		private int mErrorMessageId;
		
		public DecodeBitmapTask(File file, FileHolder fileHolder)
		{
			mFile = file;
			mFileHolder = fileHolder;
			if (fileHolder.getImageWidth() >= 2048 && fileHolder.getImageHeight() >= 2048 || fileHolder.isSvg())
			{
				mInstance.currentHolder.progressBar.setVisible(true, false);
				mInstance.currentHolder.progressBar.setIndeterminate(true);
			}
		}
		
		@Override
		protected Void doInBackground(Void... params)
		{
			FileInputStream input = null;
			byte[] magic = null;
			boolean success = false;
			try
			{
				input = new FileInputStream(mFile);
				magic = new byte[8];
				int count = input.read(magic);
				if (count == 8) success = true;
			}
			catch (IOException e)
			{
				
			}
			finally
			{
				IOUtils.close(input);
			}
			if (!success)
			{
				mErrorMessageId = R.string.message_image_corrupted;
				return null;
			}
			if (IOUtils.startsWith(magic, MAGIC_PNG))
			{
				try
				{
					mAnimatedPngDecoder = new AnimatedPngDecoder(mFileHolder);
					return null;
				}
				catch (IOException e)
				{
					
				}
			}
			else if (IOUtils.startsWith(magic, MAGIC_GIF_1) || IOUtils.startsWith(magic, MAGIC_GIF_2))
			{
				try
				{
					mGifDecoder = new GifDecoder(mFile);
					return null;
				}
				catch (IOException e)
				{
					
				}
			}
			try
			{
				int maxSize = PhotoView.getMaximumImageSize();
				mBitmap = mFileHolder.readImageBitmap(maxSize, true, true);
				if (mBitmap == null)
				{
					mErrorMessageId = R.string.message_image_corrupted;
				}
				else
				{
					if (mBitmap.getWidth() < mFileHolder.getImageWidth() ||
							mBitmap.getHeight() < mFileHolder.getImageHeight())
					{
						mDecoderDrawable = new DecoderDrawable(mBitmap, mFileHolder);
						mBitmap = null;
					}
				}
			}
			catch (OutOfMemoryError e)
			{
				mErrorMessageId = R.string.message_image_out_of_memory;
			}
			catch (Exception e)
			{
				
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result)
		{
			PagerInstance.ViewHolder holder = mInstance.currentHolder;
			holder.decodeBitmapTask = null;
			holder.progressBar.setVisible(false, false);
			if (mBitmap != null || mDecoderDrawable != null || mAnimatedPngDecoder != null || mGifDecoder != null)
			{
				if (mAnimatedPngDecoder != null)
				{
					holder.animatedPngDecoder = mAnimatedPngDecoder;
					setPhotoViewImage(holder, mAnimatedPngDecoder.getDrawable(), true);
				}
				else if (mGifDecoder != null)
				{
					holder.gifDecoder = mGifDecoder;
					setPhotoViewImage(holder, mGifDecoder.getDrawable(), true);
				}
				else if (mDecoderDrawable != null)
				{
					holder.decoderDrawable = mDecoderDrawable;
					setPhotoViewImage(holder, mDecoderDrawable, mDecoderDrawable.hasAlpha());
				}
				else
				{
					holder.simpleBitmapDrawable = new SimpleBitmapDrawable(mBitmap);
					setPhotoViewImage(holder, holder.simpleBitmapDrawable, mBitmap.hasAlpha());
				}
				holder.fullLoaded = true;
				mInstance.galleryInstance.callback.invalidateOptionsMenu();
			}
			else mInstance.callback.showError(holder, mInstance.galleryInstance.context.getString(mErrorMessageId));
		}
		
		public void cancel(PagerInstance.ViewHolder holder)
		{
			cancel(true);
			holder.progressBar.setVisible(false, true);
		}
		
		private void setPhotoViewImage(PagerInstance.ViewHolder holder, Drawable drawable, boolean hasAlpha)
		{
			holder.photoView.setImage(drawable, hasAlpha, false, holder.photoViewThumbnail);
			holder.photoViewFile = mFile;
		}
	};
}