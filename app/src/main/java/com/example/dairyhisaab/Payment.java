package com.example.dairyhisaab;

public class Payment {
    public String id;
    public String cid;
    public double amount;
    public String date;
    public String time;            // HH:mm format, e.g. "14:35"
    public String note;
    public String paidBySign;      // Base64 signature image
    public String receivedBySign;  // Base64 signature image
}
