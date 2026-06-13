package com.example.dairyhisaab;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RateEntry {
    public String id;
    public String date;
    public double rate;
    public double base;
    public double pfPerLiter;
    public String note;

    public String getFormattedDate() {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date);
            return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return date;
        }
    }
}