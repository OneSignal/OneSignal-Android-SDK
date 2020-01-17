package com.onesignal.sdktest.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.callback.EnumSelectionCallback;
import com.onesignal.sdktest.type.InAppMessage;
import com.onesignal.sdktest.type.ToastType;
import com.onesignal.sdktest.util.Animate;
import com.onesignal.sdktest.util.Font;
import com.onesignal.sdktest.util.Toaster;

public class EnumSelectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Font font;
    private LayoutInflater layoutInflater;

    private Context context;
    private Object[] enums;
    private EnumSelectionCallback callback;


    public EnumSelectionRecyclerViewAdapter(Context context, Object[] enums, EnumSelectionCallback callback) {
        this.context = context;
        this.enums = enums;
        this.callback = callback;

        this.font = new Font(context);
        this.layoutInflater = LayoutInflater.from(context);
    }


    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = layoutInflater.inflate(R.layout.enum_selection_recycler_view_item_layout, parent, false);
        view.setHasTransientState(true);
        return new EnumSelectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((EnumSelectionViewHolder) holder).setData(position, enums[position].toString());
    }

    @Override
    public int getItemCount() {
        return enums.length;
    }


    public class EnumSelectionViewHolder extends RecyclerView.ViewHolder {

        private RelativeLayout enumRelativeLayout;
        private TextView enumTextView;

        private String title;

        EnumSelectionViewHolder(View itemView) {
            super(itemView);

            enumRelativeLayout = itemView.findViewById(R.id.enum_selection_recycler_view_item_relative_layout);
            enumTextView = itemView.findViewById(R.id.enum_selection_recycler_view_item_text_view);
        }

        private void setData(int position, String title) {
            this.title = title;
            populateInterfaceElements(position);
        }

        private void populateInterfaceElements(int position) {
            enumRelativeLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.onSelection(title);
                }
            });

            font.applyFont(enumTextView, font.saralaRegular);
            enumTextView.setText(title);
        }

    }

}
