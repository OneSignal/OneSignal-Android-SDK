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
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.type.InAppMessage;
import com.onesignal.sdktest.type.ToastType;
import com.onesignal.sdktest.util.Animate;
import com.onesignal.sdktest.util.Toaster;

public class InAppMessageRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Animate animate;
    private LayoutInflater layoutInflater;
    private Toaster toaster;

    private Context context;
    private InAppMessage[] inAppMessages;


    public InAppMessageRecyclerViewAdapter(Context context, InAppMessage[] inAppMessages) {
        this.context = context;
        this.inAppMessages = inAppMessages;

        this.animate = new Animate();
        this.layoutInflater = LayoutInflater.from(context);
        this.toaster = new Toaster(context);
    }


    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = layoutInflater.inflate(R.layout.main_in_app_messages_recycler_view_item_layout, parent, false);
        view.setHasTransientState(true);
        return new InAppMessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((InAppMessageViewHolder) holder).setData(position, inAppMessages[position]);
    }

    @Override
    public int getItemCount() {
        return inAppMessages.length;
    }


    public class InAppMessageViewHolder extends RecyclerView.ViewHolder {

        private LinearLayout inAppMessageLinearLayout;
        private ImageView inAppMessageImageView;
        private ProgressBar inAppMessageProgressBar;
        private TextView inAppMessageTextView;

        private InAppMessage inAppMessage;

                            InAppMessageViewHolder(View itemView) {
            super(itemView);

            inAppMessageLinearLayout = itemView.findViewById(R.id.in_app_message_recycler_view_item_linear_layout);
            inAppMessageImageView = itemView.findViewById(R.id.in_app_message_recycler_view_item_image_view);
            inAppMessageProgressBar = itemView.findViewById(R.id.in_app_message_recycler_view_item_progress_bar);
            inAppMessageTextView = itemView.findViewById(R.id.in_app_message_recycler_view_item_text_view);
        }

        private void setData(int position, InAppMessage inAppMessage) {
            this.inAppMessage = inAppMessage;
            populateInterfaceElements(position);
        }

        private void populateInterfaceElements(int position) {
            animate.toggleAnimationView(true, View.INVISIBLE, inAppMessageImageView, inAppMessageProgressBar);

            inAppMessageLinearLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String message = "In-App Messaging Coming Soon...";
                    toaster.makeCustomViewToast(message, ToastType.INFO);
                }
            });

            inAppMessageTextView.setText(inAppMessage.getTitle());

            Glide.with(context)
                    .asBitmap()
                    .load(inAppMessage.getIconUrl())
                    .into(new BitmapImageViewTarget(inAppMessageImageView) {
                        @Override
                        protected void setResource(Bitmap resource) {
                            inAppMessageImageView.setImageBitmap(resource);
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    animate.toggleAnimationView(false, View.INVISIBLE, inAppMessageImageView, inAppMessageProgressBar);
                                }
                            }, 300);
                        }
                    });
        }

    }

}
