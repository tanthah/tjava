package com.example.translator.ui.text;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.example.translator.R;
import com.example.translator.data.model.Language;
import java.util.List;

public class LanguageSpinnerAdapter extends BaseAdapter {

    private Context context;
    private List<Language> languages;
    private LayoutInflater inflater;

    public LanguageSpinnerAdapter(Context context, List<Language> languages) {
        this.context = context;
        this.languages = languages;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return languages.size();
    }

    @Override
    public LanguageSpinnerItem getItem(int position) {
        return new LanguageSpinnerItem(languages.get(position));
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_language_spinner, parent, false);
        }

        Language language = languages.get(position);

        TextView tvLanguageName = view.findViewById(R.id.tv_language_name);
        TextView tvNativeName = view.findViewById(R.id.tv_native_name);

        tvLanguageName.setText(language.getLanguageName());
        tvNativeName.setText(language.getNativeName());

        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    public static class LanguageSpinnerItem {
        public final Language language;

        public LanguageSpinnerItem(Language language) {
            this.language = language;
        }

        @Override
        public String toString() {
            return language.getLanguageName();
        }
    }
}