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

package chan.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.preference.Preferences;

public class ChanLocator implements ChanManager.Linked
{
	private final String mChanName;
	
	private static final int HOST_TYPE_CONFIGURABLE = 0;
	private static final int HOST_TYPE_CONVERTABLE = 1;
	private static final int HOST_TYPE_SPECIAL = 2;
	
	private final LinkedHashMap<String, Integer> mHosts = new LinkedHashMap<>();
	private HttpsMode mHttpsMode = HttpsMode.NO_HTTPS;
	
	public static enum HttpsMode {NO_HTTPS, HTTPS_ONLY, CONFIGURABLE};
	
	public static class NavigationData
	{
		public static final int TARGET_THREADS = 0;
		public static final int TARGET_POSTS = 1;
		public static final int TARGET_SEARCH = 2;
		
		public final int target;
		public final String boardName;
		public final String threadNumber;
		public final String postNumber;
		public final String searchQuery;
		
		public NavigationData(int target, String boardName, String threadNumber, String postNumber, String searchQuery)
		{
			this.target = target;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
			this.searchQuery = searchQuery;
			if (target == TARGET_POSTS && StringUtils.isEmpty(threadNumber))
			{
				throw new IllegalArgumentException("threadNumber must not be empty!");
			}
		}
	}
	
	public ChanLocator()
	{
		this(false);
	}
	
	ChanLocator(boolean defaultInstance)
	{
		if (defaultInstance)
		{
			mChanName = null;
			mHttpsMode = HttpsMode.CONFIGURABLE;
		}
		else
		{
			ChanManager.checkInstancesAndThrow();
			mChanName = ChanManager.initializingChanName;
		}
	}
	
	void init()
	{
		if (getChanHosts(true).size() == 0) throw new RuntimeException("Chan hosts not defined");
	}
	
	@Override
	public final String getChanName()
	{
		return mChanName;
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends ChanLocator> T get(String chanName)
	{
		return (T) ChanManager.getInstance().getLocator(chanName, true);
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends ChanLocator> T get(Object object)
	{
		ChanManager manager = ChanManager.getInstance();
		return (T) manager.getLocator(manager.getLinkedChanName(object), false);
	}
	
	public static ChanLocator getDefault()
	{
		return ChanManager.getInstance().getLocator(null, true);
	}
	
	public final void addChanHost(String host)
	{
		mHosts.put(host, HOST_TYPE_CONFIGURABLE);
	}
	
	public final void addConvertableChanHost(String host)
	{
		mHosts.put(host, HOST_TYPE_CONVERTABLE);
	}
	
	public final void addSpecialChanHost(String host)
	{
		mHosts.put(host, HOST_TYPE_SPECIAL);
	}
	
	public final void setHttpsMode(HttpsMode httpsMode)
	{
		if (httpsMode == null) throw new NullPointerException();
		mHttpsMode = httpsMode;
	}
	
	public final boolean isHttpsConfigurable()
	{
		return mHttpsMode == HttpsMode.CONFIGURABLE;
	}
	
	public final boolean isUseHttps()
	{
		HttpsMode httpsMode = mHttpsMode;
		if (httpsMode == HttpsMode.CONFIGURABLE)
		{
			String chanName = getChanName();
			return chanName != null ? Preferences.isUseHttps(chanName) : Preferences.isUseHttpsGeneral();
		}
		return httpsMode == HttpsMode.HTTPS_ONLY;
	}
	
	public final ArrayList<String> getChanHosts(boolean confiruableOnly)
	{
		if (confiruableOnly)
		{
			ArrayList<String> hosts = new ArrayList<>();
			for (LinkedHashMap.Entry<String, Integer> entry : mHosts.entrySet())
			{
				if (entry.getValue() == HOST_TYPE_CONFIGURABLE) hosts.add(entry.getKey());
			}
			return hosts;
		}
		else return new ArrayList<>(mHosts.keySet());
	}
	
	// Can be overridden
	public boolean isChanHost(String host)
	{
		if (StringUtils.isEmpty(host)) return false;
		return mHosts.containsKey(host) || host.equals(Preferences.getDomainUnhandled(getChanName()));
	}
	
	public final boolean isChanHostOrRelative(Uri uri)
	{
		if (uri != null)
		{
			if (uri.isRelative()) return true;
			String host = uri.getHost();
			return host != null ? isChanHost(host) : false;
		}
		return false;
	}
	
	public boolean isConvertableChanHost(String host)
	{
		if (StringUtils.isEmpty(host)) return false;
		if (host.equals(Preferences.getDomainUnhandled(getChanName()))) return true;
		Integer hostType = mHosts.get(host);
		return hostType != null && (hostType == HOST_TYPE_CONFIGURABLE || hostType == HOST_TYPE_CONVERTABLE);
	}
	
	public final Uri convert(Uri uri)
	{
		if (uri != null)
		{
			String preferredScheme = getPreferredScheme();
			String host = uri.getHost();
			boolean mayConvert = uri.isRelative() || isWebScheme(uri) && isConvertableChanHost(host);
			if (mayConvert || !preferredScheme.equals(uri.getScheme()) && isChanHost(host))
			{
				Uri.Builder builder = uri.buildUpon().scheme(preferredScheme);
				if (mayConvert) builder.authority(getPreferredHost());
				return builder.build();
			}
		}
		return uri;
	}
	
	public final Uri makeRelative(Uri uri)
	{
		if (isWebScheme(uri))
		{
			String host = uri.getHost();
			if (isConvertableChanHost(host)) uri = uri.buildUpon().scheme(null).authority(null).build();
		}
		return uri;
	}
	
	// Can be overridden
	public boolean isBoardUri(Uri uri)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public boolean isThreadUri(Uri uri)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public boolean isAttachmentUri(Uri uri)
	{
		throw new UnsupportedOperationException();
	}
	
	public final boolean isImageUri(Uri uri)
	{
		return uri != null && isImageExtension(uri.getPath()) && isAttachmentUri(uri);
	}
	
	public final boolean isAudioUri(Uri uri)
	{
		return uri != null && isAudioExtension(uri.getPath()) && isAttachmentUri(uri);
	}
	
	public final boolean isVideoUri(Uri uri)
	{
		return uri != null && isVideoExtension(uri.getPath()) && isAttachmentUri(uri);
	}
	
	// Can be overridden
	public String getBoardName(Uri uri)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public String getThreadNumber(Uri uri)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public String getPostNumber(Uri uri)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public Uri createBoardUri(String boardName, int pageNumber)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public Uri createThreadUri(String boardName, String threadNumber)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public Uri createPostUri(String boardName, String threadNumber, String postNumber)
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public String createAttachmentForcedName(Uri fileUri)
	{
		return null;
	}
	
	public final String createAttachmentFileName(Uri fileUri)
	{
		return createAttachmentFileName(fileUri, createAttachmentForcedName(fileUri));
	}
	
	public final String createAttachmentFileName(Uri fileUri, String forcedName)
	{
		String fileName = forcedName != null ? forcedName : fileUri.getLastPathSegment();
		if (fileName != null) return StringUtils.escapeFile(fileName, false);
		return null;
	}
	
	public final Uri validateClickedUriString(String uriString, String boardName, String threadNumber)
	{
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (uri != null && uri.isRelative())
		{
			Uri baseUri = createThreadUri(boardName, threadNumber);
			String query = StringUtils.nullIfEmpty(uri.getQuery());
			String fragment = StringUtils.nullIfEmpty(uri.getFragment());
			Uri.Builder builder = baseUri.buildUpon().encodedQuery(query).encodedFragment(fragment);
			String path = uri.getPath();
			if (!StringUtils.isEmpty(path)) builder.encodedPath(path);
			return builder.build();
		}
		return uri;
	}
	
	public NavigationData handleUriClickSpecial(Uri uri)
	{
		return null;
	}
	
	public final boolean isWebScheme(Uri uri)
	{
		String scheme = uri.getScheme();
		return "http".equals(scheme) || "https".equals(scheme);
	}
	
	public final boolean isImageExtension(String path)
	{
		return C.IMAGE_EXTENSIONS.contains(getFileExtension(path));
	}
	
	public final boolean isAudioExtension(String path)
	{
		return C.AUDIO_EXTENSIONS.contains(getFileExtension(path));
	}
	
	public final boolean isVideoExtension(String path)
	{
		return C.VIDEO_EXTENSIONS.contains(getFileExtension(path));
	}
	
	public final String getFileExtension(String path)
	{
		return StringUtils.getFileExtension(path);
	}
	
	public final String convertPreferredHost(String host)
	{
		String firstConfigurableHost = null;
		boolean singleConfigurableHost = true;
		for (LinkedHashMap.Entry<String, Integer> entry : mHosts.entrySet())
		{
			if (entry.getValue() == HOST_TYPE_CONFIGURABLE)
			{
				if (firstConfigurableHost == null) firstConfigurableHost = entry.getKey();
				else if (singleConfigurableHost) singleConfigurableHost = false;
				else break;
			}
		}
		if (StringUtils.isEmpty(host) || singleConfigurableHost) host = firstConfigurableHost;
		return host;
	}
	
	public final String getPreferredHost()
	{
		String host = Preferences.getDomainUnhandled(getChanName());
		if (StringUtils.isEmpty(host))
		{
			for (LinkedHashMap.Entry<String, Integer> entry : mHosts.entrySet())
			{
				if (entry.getValue() == HOST_TYPE_CONFIGURABLE)
				{
					host = entry.getKey();
					break;
				}
			}
		}
		return host;
	}
	
	public final void setPreferredHost(String host)
	{
		if (host == null || getChanHosts(true).get(0).equals(host)) host = "";
		Preferences.setDomainUnhandled(mChanName, host);
	}
	
	private static String getPreferredScheme(boolean useHttps)
	{
		return useHttps ? "https" : "http";
	}
	
	private String getPreferredScheme()
	{
		return getPreferredScheme(isUseHttps());
	}

	public final Uri buildPath(String... segments)
	{
		return buildPathWithHost(getPreferredHost(), segments);
	}

	public final Uri buildPathWithHost(String host, String... segments)
	{
		return buildPathWithSchemeHost(isUseHttps(), host, segments);
	}
	
	public final Uri buildPathWithSchemeHost(boolean useHttps, String host, String... segments)
	{
		Uri.Builder builder = new Uri.Builder().scheme(getPreferredScheme(useHttps)).authority(host);
		for (int i = 0; i < segments.length; i++)
		{
			String segment = segments[i];
			if (segment != null) builder.appendEncodedPath(segment.replaceFirst("^/+", ""));
		}
		return builder.build();
	}
	
	public final Uri buildQuery(String path, String... alternation)
	{
		return buildQueryWithHost(getPreferredHost(), path, alternation);
	}
	
	public final Uri buildQueryWithHost(String host, String path, String... alternation)
	{
		return buildQueryWithSchemeHost(isUseHttps(), host, path, alternation);
	}
	
	public final Uri buildQueryWithSchemeHost(boolean useHttps, String host, String path, String... alternation)
	{
		Uri.Builder builder = new Uri.Builder().scheme(getPreferredScheme(useHttps)).authority(host);
		if (path != null) builder.appendEncodedPath(path.replaceFirst("^/+", ""));
		if (alternation.length % 2 != 0)
		{
			throw new IllegalArgumentException("Length of alternation must be a multiple of 2.");
		}
		for (int i = 0; i < alternation.length; i += 2)
		{
			builder.appendQueryParameter(alternation[i], alternation[i + 1]);
		}
		return builder.build();
	}
	
	public final Uri setScheme(Uri uri)
	{
		if (uri != null && StringUtils.isEmpty(uri.getScheme()))
		{
			return uri.buildUpon().scheme(getPreferredScheme()).build();
		}
		return uri;
	}
	
	private static final Pattern YOUTUBE_URI = Pattern.compile("(?:https?://)(?:www\\.)?(?:m\\.)?" +
			"youtu(?:\\.be/|be\\.com/(?:v/|(?:#/)?watch\\?(?:.*?|)v=))([\\w\\-]{11})");
	
	private static final Pattern VIMEO_URI = Pattern.compile("(?:https?://)(?:player\\.)?vimeo.com/(?:video/)?" +
			"(?:channels/staffpicks/)?(\\d+)");
	
	private static final Pattern VOCAROO_URI = Pattern.compile("(?:https?://)(?:www\\.)?vocaroo\\.com" +
			"/(?:player\\.swf\\?playMediaID=|i/|media_command\\.php\\?media=)([\\w\\-]{12})");
	
	private static final Pattern SOUNDCLOUD_URI = Pattern.compile("(?:https?://)soundcloud\\.com/([\\w/_-]*)");
	
	private final boolean isMayContainEmbeddedCode(String text, String what)
	{
		return !StringUtils.isEmpty(text) && text.contains(what);
	}
	
	public final String getYouTubeEmbeddedCode(String text)
	{
		if (!isMayContainEmbeddedCode(text, "youtu")) return null;
		return getGroupValue(text, YOUTUBE_URI, 1);
	}
	
	public final String[] getYouTubeEmbeddedCodes(String text)
	{
		if (!isMayContainEmbeddedCode(text, "youtu")) return null;
		return getUniqueGroupValues(text, YOUTUBE_URI, 1);
	}
	
	public final String getVimeoEmbeddedCode(String text)
	{
		if (!isMayContainEmbeddedCode(text, "vimeo")) return null;
		return getGroupValue(text, VIMEO_URI, 1);
	}
	
	public final String[] getVimeoEmbeddedCodes(String text)
	{
		if (!isMayContainEmbeddedCode(text, "vimeo")) return null;
		return getUniqueGroupValues(text, VIMEO_URI, 1);
	}
	
	public final String getVocarooEmbeddedCode(String text)
	{
		if (!isMayContainEmbeddedCode(text, "vocaroo")) return null;
		return getGroupValue(text, VOCAROO_URI, 1);
	}
	
	public final String[] getVocarooEmbeddedCodes(String text)
	{
		if (!isMayContainEmbeddedCode(text, "vocaroo")) return null;
		return getUniqueGroupValues(text, VOCAROO_URI, 1);
	}
	
	public final String getSoundCloudEmbeddedCode(String text)
	{
		if (!isMayContainEmbeddedCode(text, "soundcloud")) return null;
		String value = getGroupValue(text, SOUNDCLOUD_URI, 1);
		if (value != null && value.contains("/")) return value;
		return null;
	}
	
	public final String[] getSoundCloudEmbeddedCodes(String text)
	{
		if (!isMayContainEmbeddedCode(text, "soundcloud")) return null;
		String[] embeddedCodes = getUniqueGroupValues(text, SOUNDCLOUD_URI, 1);
		if (embeddedCodes != null)
		{
			int deleteCount = 0;
			for (int i = 0; i < embeddedCodes.length; i++)
			{
				if (!embeddedCodes[i].contains("/"))
				{
					embeddedCodes[i] = null;
					deleteCount++;
				}
			}
			if (deleteCount > 0)
			{
				int newLength = embeddedCodes.length - deleteCount;
				if (newLength == 0) return null;
				String[] newEmbeddedCodes = new String[newLength];
				for (int i = 0, j = 0; i < embeddedCodes.length; i++)
				{
					String embeddedCode = embeddedCodes[i];
					if (embeddedCode != null) newEmbeddedCodes[j++] = embeddedCode;
				}
				return newEmbeddedCodes;
			}
			else return embeddedCodes;
		}
		return null;
	}
	
	public final boolean isPathMatches(Uri uri, Pattern pattern)
	{
		if (uri != null)
		{
			String path = uri.getPath();
			if (path != null) return pattern.matcher(path).matches();
		}
		return false;
	}
	
	public final String getGroupValue(String from, Pattern pattern, int groupIndex)
	{
		if (from == null) return null;
		Matcher matcher = pattern.matcher(from);
		if (matcher.find() && matcher.groupCount() > 0) return matcher.group(groupIndex);
		return null;
	}
	
	public final String[] getUniqueGroupValues(String from, Pattern pattern, int groupIndex)
	{
		if (from == null) return null;
		Matcher matcher = pattern.matcher(from);
		LinkedHashSet<String> data = new LinkedHashSet<>();
		while (matcher.find() && matcher.groupCount() > 0) data.add(matcher.group(groupIndex));
		return data.size() > 0 ? data.toArray(new String[data.size()]) : null;
	}
}