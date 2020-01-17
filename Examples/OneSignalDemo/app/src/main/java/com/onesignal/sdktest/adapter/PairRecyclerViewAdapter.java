package com.onesignal.sdktest.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.onesignal.sdktest.R;
import com.onesignal.sdktest.callback.PairItemActionCallback;
import com.onesignal.sdktest.util.Util;

import java.util.ArrayList;
import java.util.Map;

public class PairRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private LayoutInflater layoutInflater;

    private Context context;

    private ArrayList<Map.Entry> tags;
    private PairItemActionCallback callback;

    public PairRecyclerViewAdapter(Context context, ArrayList<Map.Entry> tags, PairItemActionCallback callback) {
        this.context = context;

        this.tags = tags;
        this.callback = callback;

        layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        View view = layoutInflater.inflate(R.layout.pair_recycler_view_item_layout, parent, false);
        view.setHasTransientState(true);
        return new PairViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((PairViewHolder) holder).setData(position, tags.get(position));
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    public class PairViewHolder extends RecyclerView.ViewHolder {

        private RelativeLayout pairRelativeLayout;
        private TextView pairKeyTextView;
        private TextView pairValueTextView;

        private Map.Entry pair;

        PairViewHolder(View itemView) {
            super(itemView);

            pairRelativeLayout = itemView.findViewById(R.id.pair_recycler_view_item_relative_layout);
            pairKeyTextView = itemView.findViewById(R.id.pair_recycler_view_item_key_text_view);
            pairValueTextView = itemView.findViewById(R.id.pair_recycler_view_item_value_text_view);
        }

        private void setData(int position, Map.Entry pair) {
            this.pair = pair;
            populateInterfaceElements(position);
        }

        private void populateInterfaceElements(final int position) {

            pairKeyTextView.setText(pair.getKey().toString());

            String value = pair.getValue().toString();

            if (Util.isBoolean(value))
                value += " (bool)";
            else if (Util.isNumeric(value))
                value += " (num)";
            else
                value += " (str)";
            pairValueTextView.setText(value);

            pairRelativeLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    callback.onLongClick(pair.getKey().toString());
                    return false;
                }
            });

        }

    }

}
