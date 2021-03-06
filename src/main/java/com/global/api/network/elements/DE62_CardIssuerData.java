package com.global.api.network.elements;

import com.global.api.network.abstractions.IDataElement;
import com.global.api.network.enums.CardIssuerEntryTag;
import com.global.api.utils.ReverseStringEnumMap;
import com.global.api.utils.StringParser;
import com.global.api.utils.StringUtils;

import java.util.HashMap;

public class DE62_CardIssuerData implements IDataElement<DE62_CardIssuerData> {
    private HashMap<String, DE62_2_CardIssuerEntry> cardIssuerEntries;

    public int getNumEntries() {
        return cardIssuerEntries.size();
    }
    public HashMap<String, DE62_2_CardIssuerEntry> getCardIssuerEntries() {
        return cardIssuerEntries;
    }
    public void setCardIssuerEntries(HashMap<String, DE62_2_CardIssuerEntry> cardIssuerEntries) {
        this.cardIssuerEntries = cardIssuerEntries;
    }

    public DE62_CardIssuerData() {
        cardIssuerEntries = new HashMap<String, DE62_2_CardIssuerEntry>();
    }

    public void add(DE62_2_CardIssuerEntry entry) {
        if(!StringUtils.isNullOrEmpty(entry.getIssuerEntry())) {
            cardIssuerEntries.put(entry.getIssuerTagValue(), entry);
        }
    }
    public void add(CardIssuerEntryTag tag, String value) {
        add(new DE62_2_CardIssuerEntry(tag, value));
    }
    public void add(CardIssuerEntryTag tag, String tagValue, String value) {
        add(new DE62_2_CardIssuerEntry(tag, tagValue, value));
    }

    public String get(CardIssuerEntryTag tag) {
        for(DE62_2_CardIssuerEntry entry: cardIssuerEntries.values()) {
            if(entry.getIssuerTag().equals(tag)) {
                return entry.getIssuerEntry();
            }
        }
        return null;
    }
    public String get(String tagValue) {
        for(DE62_2_CardIssuerEntry entry: cardIssuerEntries.values()) {
            if(entry.getIssuerTagValue().equals(tagValue)) {
                return entry.getIssuerEntry();
            }
        }
        return null;
    }

    public DE62_CardIssuerData fromByteArray(byte[] buffer) {
        StringParser sp = new StringParser(buffer);

        int numEntries = sp.readInt(2);
        for(int i = 0; i < numEntries; i++) {
            String tagValue = sp.readString(3);
            CardIssuerEntryTag tag = ReverseStringEnumMap.parse(tagValue, CardIssuerEntryTag.class);
            if(tag == null) { // find one of the other values
                tag = CardIssuerEntryTag.findPartial(tagValue);
            }
            String issuerEntryData = sp.readLLVAR();

            DE62_2_CardIssuerEntry entry = new DE62_2_CardIssuerEntry(tag, issuerEntryData);
            entry.setIssuerTagValue(tagValue);
            cardIssuerEntries.put(entry.getIssuerTag().getValue(), entry);
        }

        return this;
    }

    public byte[] toByteArray() {
        String rvalue = StringUtils.padLeft(cardIssuerEntries.size(), 2, '0');

        for(DE62_2_CardIssuerEntry entry: cardIssuerEntries.values()) {
            // put the tag value if present
            if(!StringUtils.isNullOrEmpty(entry.getIssuerTagValue())) {
                rvalue = rvalue.concat(entry.getIssuerTagValue());
            }
            else {
                rvalue = rvalue.concat(entry.getIssuerTag().getValue());
            }

            // put the entry value
            rvalue = rvalue.concat(StringUtils.padLeft(entry.getIssuerEntry().length(), 2, '0'))
                    .concat(entry.getIssuerEntry());
        }

        return rvalue.getBytes();
    }

    public String toString() {
        return new String(toByteArray());
    }
}
