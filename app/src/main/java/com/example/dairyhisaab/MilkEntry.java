package com.example.dairyhisaab;

public class MilkEntry {
    public String id;
    public String cid;      // customer id
    public String date;     // yyyy-MM-dd
    public String shift;    // "morning" or "evening"
    public double qty;      // litres
    public double fat;      // fat %
    public double rate;     // rate per litre (calculated)
}
