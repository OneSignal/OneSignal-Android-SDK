package com.onesignal.sdktest.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.onesignal.sdktest.R;
import com.onesignal.sdktest.callback.SingleItemActionCallback;
import com.onesignal.sdktest.callback.SubscriptionItemActionCallback;
import com.onesignal.user.subscriptions.IEmailSubscription;
import com.onesignal.user.subscriptions.ISmsSubscription;
import com.onesignal.user.subscriptions.ISubscription;

import java.util.ArrayList;

public class SubscriptionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final LayoutInflater layoutInflater;

    private final Context context;

    private final ArrayList<ISubscription> subscriptions;
    private final SubscriptionItemActionCallback callback;

    public SubscriptionRecyclerViewAdapter(Context context, ArrayList<ISubscription> subscriptions, SubscriptionItemActionCallback callback) {
        this.context = context;

        this.subscriptions = subscriptions;
        this.callback = callback;

        layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        View view = layoutInflater.inflate(R.layout.subscription_recycler_view_item_layout, parent, false);
        view.setHasTransientState(true);
        return new SubscriptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((SubscriptionViewHolder) holder).setData(position, subscriptions.get(position));
    }

    @Override
    public int getItemCount() {
        return subscriptions.size();
    }

    public class SubscriptionViewHolder extends RecyclerView.ViewHolder {

        private final LinearLayout singleLinearLayout;
        private final TextView idTextView;
        private final TextView addressTitleTextView;
        private final TextView addressTextView;

        private ISubscription item;

        SubscriptionViewHolder(View itemView) {
            super(itemView);

            singleLinearLayout = itemView.findViewById(R.id.subscription_recycler_view_item_linear_layout);
            idTextView = itemView.findViewById(R.id.subscription_recycler_view_item_id_text_view);
            addressTitleTextView = itemView.findViewById(R.id.subscription_recycler_view_item_address_title_text_view);
            addressTextView = itemView.findViewById(R.id.subscription_recycler_view_item_address_text_view);
        }

        private void setData(int position, ISubscription item) {
            this.item = item;
            populateInterfaceElements(position);
        }

        private void populateInterfaceElements(final int position) {
            idTextView.setText(item.getId());

            if(item instanceof IEmailSubscription) {
                addressTitleTextView.setText(R.string.email_colon);
                addressTextView.setText(((IEmailSubscription) item).getEmail());
            }
            else if(item instanceof ISmsSubscription) {
                addressTitleTextView.setText(R.string.sms_colon);
                addressTextView.setText(((ISmsSubscription) item).getNumber());
            }

            singleLinearLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    callback.onLongClick(item);
                    return false;
                }
            });

        }

    }

}
