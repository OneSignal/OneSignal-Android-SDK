package com.onesignal.sdktest.ui;

import android.content.Context;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.onesignal.sdktest.R;

public class RecyclerViewBuilder {

    private Context context;


    public RecyclerViewBuilder(Context context) {
        this.context = context;
    }

    public void setupRecyclerView(RecyclerView recyclerView, int viewCache,  boolean hasDivider, boolean isVertical) {
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();

        int orientation = isVertical ? DividerItemDecoration.VERTICAL : DividerItemDecoration.HORIZONTAL;
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context, orientation);

        int divider = hasDivider ? R.drawable.divider : R.drawable.no_divider;
        dividerItemDecoration.setDrawable(context.getResources().getDrawable(divider));

        recyclerView.setItemAnimator(defaultItemAnimator);
        recyclerView.addItemDecoration(dividerItemDecoration);
        recyclerView.setHasFixedSize(false);
        recyclerView.setItemViewCacheSize(viewCache);
    }

}
