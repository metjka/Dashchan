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

package com.mishiranu.dashchan.ui.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.UiManager;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class SearchAdapter extends BaseAdapter implements BusyScrollListener.Callback
{
	private static final int TYPE_VIEW = 0;
	private static final int TYPE_HEADER = 1;
	
	private final Context mContext;
	
	private final UiManager mUiManager;
	private final UiManager.DemandSet mDemandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet mConfigurationSet;
	
	private final ArrayList<PostItem> mPostItems = new ArrayList<>();
	private final ArrayList<Object> mGroupItems = new ArrayList<>();
	
	private boolean mGroupMode = false;
	
	public SearchAdapter(Context context, UiManager uiManager)
	{
		mContext = context;
		mUiManager = uiManager;
		mConfigurationSet = new UiManager.ConfigurationSet(null, null, new HidePerformer(),
				new GalleryItem.GallerySet(false), null, null, true, false, false, false, null);
	}
	
	@Override
	public int getViewTypeCount()
	{
		return 2;
	}
	
	@Override
	public int getItemViewType(int position)
	{
		Object item = getItem(position);
		return item instanceof PostItem ? TYPE_VIEW : TYPE_HEADER;
	}
	
	@Override
	public boolean isEnabled(int position)
	{
		Object item = getItem(position);
		return item instanceof PostItem;
	}
	
	@Override
	public boolean areAllItemsEnabled()
	{
		return false;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		Object item = getItem(position);
		if (item instanceof PostItem)
		{
			return mUiManager.view().getPostView((PostItem) item, convertView, parent, mDemandSet, mConfigurationSet);
		}
		else
		{
			if (convertView == null)
			{
				convertView = ViewFactory.makeListTextHeader(mContext, LayoutInflater.from(mContext), false);
				if (C.API_LOLLIPOP)
				{
					float density = ResourceUtils.obtainDensity(mContext);
					convertView.setPadding((int) (12f * density), convertView.getPaddingTop() + (int) (12f * density),
							(int) (12f * density), convertView.getPaddingBottom());
				}
			}
			((TextView) convertView).setText((String) item);
			return convertView;
		}
	}
	
	@Override
	public int getCount()
	{
		return mGroupMode ? mGroupItems.size() : mPostItems.size();
	}
	
	@Override
	public Object getItem(int position)
	{
		return mGroupMode ? mGroupItems.get(position) : mPostItems.get(position);
	}
	
	public PostItem getPostItem(int position)
	{
		Object item = getItem(position);
		if (item instanceof PostItem) return (PostItem) item;
		return null;
	}
	
	@Override
	public long getItemId(int position)
	{
		return 0;
	}
	
	@Override
	public void setBusy(boolean isBusy, AbsListView view)
	{
		if (!isBusy)
		{
			int count = view.getChildCount();
			for (int i = 0; i < count; i++)
			{
				View v = view.getChildAt(i);
				int position = view.getPositionForView(v);
				PostItem postItem = getPostItem(position);
				if (postItem != null) mUiManager.view().displayThumbnail(v, postItem.getAttachmentItems(), false);
			}
		}
		mDemandSet.isBusy = isBusy;
	}
	
	public void setItems(PostItem[] postItems)
	{
		mPostItems.clear();
		if (postItems != null) Collections.addAll(mPostItems, postItems);
		handleItems();
	}
	
	public void setGroupMode(boolean groupMode)
	{
		if (mGroupMode != groupMode)
		{
			mGroupMode = groupMode;
			handleItems();
		}
	}
	
	public boolean isGroupMode()
	{
		return mGroupMode;
	}
	
	private void handleItems()
	{
		mGroupItems.clear();
		mConfigurationSet.gallerySet.clear();
		if (mPostItems.size() > 0)
		{
			if (mGroupMode)
			{
				LinkedHashMap<String, ArrayList<PostItem>> map = new LinkedHashMap<>();
				for (PostItem postItem : mPostItems)
				{
					String threadNumber = postItem.getThreadNumber();
					ArrayList<PostItem> postItems = map.get(threadNumber);
					if (postItems == null)
					{
						postItems = new ArrayList<>();
						map.put(threadNumber, postItems);
					}
					postItems.add(postItem);
				}
				for (LinkedHashMap.Entry<String, ArrayList<PostItem>> entry : map.entrySet())
				{
					String threadNumber = entry.getKey();
					boolean number;
					try
					{
						Integer.parseInt(threadNumber);
						number = true;
					}
					catch (NumberFormatException e)
					{
						number = false;
					}
					mGroupItems.add(mContext.getString(R.string.text_in_thread_format_format,
							number ? "#" + threadNumber : threadNumber));
					int ordinalIndex = 0;
					for (PostItem postItem : entry.getValue())
					{
						mGroupItems.add(postItem);
						postItem.setOrdinalIndex(ordinalIndex++);
					}
				}
			}
			else
			{
				for (int i = 0; i < mPostItems.size(); i++) mPostItems.get(i).setOrdinalIndex(i);
			}
		}
		for (int i = 0, count = getCount(); i < count; i++)
		{
			PostItem postItem = getPostItem(i);
			if (postItem != null) mConfigurationSet.gallerySet.add(postItem.getAttachmentItems());
		}
		notifyDataSetChanged();
	}
}