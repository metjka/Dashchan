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

package com.mishiranu.dashchan.async;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.net.CaptchaServiceReader;
import com.mishiranu.dashchan.net.RecaptchaReader;
import com.mishiranu.dashchan.util.GraphicsUtils;

public class ReadCaptchaTask extends CancellableTask<Void, Long, Boolean>
{
	public static final String RECAPTCHA_SKIP_RESPONSE = "recaptcha_skip_response";
	
	private final Context mContext;
	private final Callback mCallback;
	private final HttpHolder mHolder = new HttpHolder();
	
	private final String mCaptchaType;
	private final String[] mCaptchaPass;
	private final boolean mMayShowLoadButton;
	private final String mRequirement;
	private final String mChanName;
	private final String mBoardName;
	private final String mThreadNumber;
	
	private ChanPerformer.CaptchaState mCaptchaState;
	private ChanPerformer.CaptchaData mCaptchaData;
	private String mLoadedCaptchaType;
	private ChanConfiguration.Captcha.Input mInput;
	private ChanConfiguration.Captcha.Validity mValidity;
	private Bitmap mImage;
	private boolean mLarge;
	private boolean mBlackAndWhite;
	private ErrorItem mErrorItem;

	public static interface Callback
	{
		public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
				Bitmap image, boolean large, boolean blackAndWhite);
		public void onReadCaptchaError(ErrorItem errorItem);
	}
	
	public ReadCaptchaTask(Context context, Callback callback, String captchaType, String requirement,
			String[] captchaPass, boolean mayShowLoadButton, String chanName, String boardName, String threadNumber)
	{
		mContext = context;
		mCallback = callback;
		mCaptchaType = captchaType;
		mCaptchaPass = captchaPass;
		mMayShowLoadButton = mayShowLoadButton;
		mRequirement = requirement;
		mChanName = chanName;
		mBoardName = boardName;
		mThreadNumber = threadNumber;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		Thread thread = Thread.currentThread();
		ChanPerformer.ReadCaptchaResult result;
		ChanConfiguration configuration = ChanConfiguration.get(mChanName);
		try
		{
			String parentCaptchaType = configuration.getCaptchaParentType(mCaptchaType);
			result = ChanPerformer.get(mChanName).onReadCaptcha(new ChanPerformer.ReadCaptchaData(parentCaptchaType,
					mCaptchaPass, mMayShowLoadButton, mRequirement, mBoardName, mThreadNumber, mHolder));
		}
		catch (HttpException | InvalidResponseException e)
		{
			mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		catch (LinkageError | RuntimeException e)
		{
			mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
			return false;
		}
		finally
		{
			ChanConfiguration.get(mChanName).commit();
		}
		mCaptchaState = result.captchaState;
		mCaptchaData = result.captchaData;
		mLoadedCaptchaType = result.captchaType != null ? configuration.getCaptchaPreferredChildType(result.captchaType,
				mCaptchaType) : null;
		mInput = result.input;
		mValidity = result.validity;
		String captchaType = mLoadedCaptchaType != null ? mLoadedCaptchaType : mCaptchaType;
		boolean newRecaptcha = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT.equals(captchaType) ||
				ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK.equals(captchaType);
		boolean oldRecaptcha = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_1_JAVASCRIPT.equals(captchaType) ||
				ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_1_NOSCRIPT.equals(captchaType);
		boolean yandex = ChanConfiguration.CAPTCHA_TYPE_YANDEX_NUMERIC.equals(captchaType) ||
				ChanConfiguration.CAPTCHA_TYPE_YANDEX_TEXTUAL.equals(captchaType);
		boolean mailru = ChanConfiguration.CAPTCHA_TYPE_MAILRU.equals(captchaType);
		if (mCaptchaState != ChanPerformer.CaptchaState.CAPTCHA) return true;
		if (thread.isInterrupted()) return null;
		if (mMayShowLoadButton && newRecaptcha)
		{
			String apiKey = mCaptchaData.get(ChanPerformer.CaptchaData.API_KEY);
			if (ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT.equals(captchaType))
			{
				RecaptchaReader.getInstance().preloadNewWidget(apiKey);
			}
			mCaptchaState = ChanPerformer.CaptchaState.NEED_LOAD;
			return true;
		}
		if (newRecaptcha || oldRecaptcha)
		{
			String apiKey = mCaptchaData.get(ChanPerformer.CaptchaData.API_KEY);
			try
			{
				RecaptchaReader recaptchaReader = RecaptchaReader.getInstance();
				String challenge;
				if (newRecaptcha)
				{
					challenge = recaptchaReader.getChallenge2(mHolder, apiKey,
							!ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK.equals(captchaType));
				}
				else if (oldRecaptcha)
				{
					challenge = recaptchaReader.getChallenge1(mHolder, apiKey,
							!ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_1_NOSCRIPT.equals(captchaType));
				}
				else throw new RuntimeException();
				mCaptchaData.put(ChanPerformer.CaptchaData.CHALLENGE, challenge);
				if (thread.isInterrupted()) return null;
				Pair<Bitmap, Boolean> pair;
				if (newRecaptcha) pair = recaptchaReader.getImage2(mHolder, apiKey, challenge, null, true);
				else if (oldRecaptcha) pair = recaptchaReader.getImage1(mHolder, challenge, true);
				else throw new RuntimeException();
				mImage = pair.first;
				mBlackAndWhite = pair.second;
				return true;
			}
			catch (RecaptchaReader.SkipException e)
			{
				mCaptchaData.put(RECAPTCHA_SKIP_RESPONSE, e.getResponse());
				mCaptchaState = ChanPerformer.CaptchaState.SKIP;
				return true;
			}
			catch (RecaptchaReader.CancelException e)
			{
				mCaptchaState = ChanPerformer.CaptchaState.NEED_LOAD;
				return true;
			}
			catch (HttpException e)
			{
				mErrorItem = e.getErrorItemAndHandle();
				return false;
			}
		}
		else if (yandex || mailru)
		{
			CaptchaServiceReader reader = CaptchaServiceReader.getInstance();
			CaptchaServiceReader.Result captchaResult;
			try
			{
				if (yandex)
				{
					captchaResult = reader.readYandex(mContext, mHolder, mCaptchaData
							.get(ChanPerformer.CaptchaData.CHALLENGE));
				}
				else if (mailru)
				{
					captchaResult = reader.readMailru(mHolder, mChanName, mCaptchaData
							.get(ChanPerformer.CaptchaData.API_KEY));
				}
				else throw new RuntimeException();
			}
			catch (HttpException | InvalidResponseException e)
			{
				mErrorItem = e.getErrorItemAndHandle();
				return false;
			}
			mCaptchaData.put(ChanPerformer.CaptchaData.CHALLENGE, captchaResult.challenge);
			mImage = captchaResult.image;
			mBlackAndWhite = captchaResult.blackAndWhite;
			return true;
		}
		else if (result.image != null)
		{
			Bitmap image = result.image;
			mBlackAndWhite = GraphicsUtils.isBlackAndWhiteCaptchaImage(image);
			mImage = mBlackAndWhite ? GraphicsUtils.handleBlackAndWhiteCaptchaImage(image).first : image;
			mLarge = result.large;
			return true;
		}
		mErrorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
		return false;
	}
	
	@Override
	protected void onPostExecute(Boolean result)
	{
		if (result)
		{
			mCallback.onReadCaptchaSuccess(mCaptchaState, mCaptchaData, mLoadedCaptchaType, mInput, mValidity,
					mImage, mLarge, mBlackAndWhite);
		}
		else mCallback.onReadCaptchaError(mErrorItem);
	}
	
	@Override
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
	}
}