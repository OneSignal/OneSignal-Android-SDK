package com.onesignal;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.onesignal.example.R;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class StringRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private LayoutInflater layoutInflater;

    private Context context;

    private ArrayList<String> ids;

    StringRecyclerViewAdapter(Context context, JSONArray ids) {
        this.context = context;
        this.layoutInflater = LayoutInflater.from(context);

        this.ids = new ArrayList<>();
        this.ids.addAll(convertJsonArrayToStringArrayList(ids));
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        View view = layoutInflater.inflate(com.onesignal.example.R.layout.string_recycler_view_child_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((ViewHolder) holder).setData(position, ids.get(position));
    }

    @Override
    public int getItemCount() {
        return ids.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView stringTextView;

        private String id;

        ViewHolder(View itemView) {
            super(itemView);

            stringTextView = itemView.findViewById(R.id.string_recycler_view_child_text_view);
        }

        private void setData(int position, String id) {
            this.id = id;
            populateInterfaceElements(position);
        }

        private void populateInterfaceElements(final int position) {
            stringTextView.setText(id);
        }

    }

    void setIds(JSONArray ids) {
        this.ids.clear();
        this.ids.addAll(convertJsonArrayToStringArrayList(ids));
        this.notifyDataSetChanged();
    }

    ArrayList<String> convertJsonArrayToStringArrayList(JSONArray jsonArray) {
        ArrayList<String> strings = new ArrayList<>();
        try {
            ArrayList<Object> idObjects = new ArrayList<>(JSONUtils.jsonArrayToList(jsonArray));
            for (Object  idObject : idObjects) {
                strings.add(idObject.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return strings;
    }

}