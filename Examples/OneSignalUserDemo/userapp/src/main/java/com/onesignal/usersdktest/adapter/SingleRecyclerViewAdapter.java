package com.onesignal.usersdktest.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.onesignal.usersdktest.R;
import com.onesignal.usersdktest.callback.SingleItemActionCallback;
import com.onesignal.usersdktest.util.Util;

import java.util.ArrayList;

public class SingleRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private LayoutInflater layoutInflater;

    private Context context;

    private ArrayList<Object> values;
    private SingleItemActionCallback callback;

    public SingleRecyclerViewAdapter(Context context, ArrayList<Object> values, SingleItemActionCallback callback) {
        this.context = context;

        this.values = values;
        this.callback = callback;

        layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        View view = layoutInflater.inflate(R.layout.single_recycler_view_item_layout, parent, false);
        view.setHasTransientState(true);
        return new SingleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((SingleViewHolder) holder).setData(position, values.get(position));
    }

    @Override
    public int getItemCount() {
        return values.size();
    }

    public class SingleViewHolder extends RecyclerView.ViewHolder {

        private RelativeLayout singleRelativeLayout;
        private TextView singleTextView;

        private Object item;

        SingleViewHolder(View itemView) {
            super(itemView);

            singleRelativeLayout = itemView.findViewById(R.id.single_recycler_view_item_relative_layout);
            singleTextView = itemView.findViewById(R.id.single_recycler_view_item_text_view);
        }

        private void setData(int position, Object item) {
            this.item = item;
            populateInterfaceElements(position);
        }

        private void populateInterfaceElements(final int position) {
            String value = item.toString();

            if (Util.isBoolean(value))
                value += " (bool)";
            else if (Util.isNumeric(value))
                value += " (num)";
            else
                value += " (str)";
            singleTextView.setText(value);

            singleRelativeLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    callback.onLongClick(item.toString());
                    return false;
                }
            });

        }

    }

}
