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

package com.mishiranu.dashchan.preference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import android.os.Bundle;
import android.preference.Preference;

import chan.content.ChanConfiguration;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;

public class CookiesFragment extends BasePreferenceFragment implements Comparator<Map.Entry<String, String>>
{
	private ChanConfiguration mConfiguration;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		String chanName = getActivity().getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		if (chanName == null) throw new IllegalStateException();
		mConfiguration = ChanConfiguration.get(chanName);
		getActivity().setTitle(R.string.preference_delete_cookies);
		Map<String, String> cookies = mConfiguration.getAllCookieNames();
		
		ArrayList<Map.Entry<String, String>> entries = new ArrayList<>(cookies.entrySet());
		Collections.sort(entries, this);
		for (Map.Entry<String, String> entry : entries)
		{
			String cookie = mConfiguration.getCookie(entry.getKey());
			Preference preference = makeButton(null, entry.getValue(), cookie, false);
			if (StringUtils.isEmpty(cookie)) preference.setEnabled(false);
			else preference.setOnPreferenceClickListener(this);
			preference.setKey(entry.getKey());
		}
	}
	
	@Override
	public int compare(Map.Entry<String, String> lhs, Map.Entry<String, String> rhs)
	{
		return StringUtils.compare(lhs.getKey(), rhs.getKey(), true);
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference)
	{
		preference.setEnabled(false);
		preference.setSummary(null);
		mConfiguration.storeCookie(preference.getKey(), null, null);
		mConfiguration.commit();
		return true;
	}
}