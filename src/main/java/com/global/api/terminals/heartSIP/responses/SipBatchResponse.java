package com.global.api.terminals.heartSIP.responses;

import com.global.api.entities.exceptions.ApiException;
import com.global.api.terminals.abstractions.IBatchCloseResponse;
import com.global.api.utils.Element;

public class SipBatchResponse extends SipBaseResponse implements IBatchCloseResponse {
    private String totalCount;
    private String totalAmount;

    public String getTotalCount() {
        return totalCount;
    }
    public void setTotalCount(String totalCount) {
        this.totalCount = totalCount;
    }
    public String getTotalAmount() {
        return totalAmount;
    }
    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SipBatchResponse(byte[] buffer, String... messageIds) throws ApiException {
        super(buffer, messageIds);
    }

    protected void mapResponse(Element response) {
        super.mapResponse(response);

        Element[] cardRecords = response.getAll("CardSummaryRecord");
        for(Element record: cardRecords) {}

        Element[] transactionRecords = response.getAll("TransactionSummaryReport");
        for(Element record: transactionRecords) {}

        Element details = response.get("BatchDetailRecord");
        //details.GetValue<string>("ReferenceNumber");
        //details.GetValue<string>("TransactionTime");
        //details.GetValue<string>("MaskedPAN");
        //details.GetValue<string>("CardType");
        //details.GetValue<string>("TransactionType");
        //details.GetValue<string>("CardAcquisition");
        //details.GetValue<string>("ApprovalCode");
        //details.GetValue<string>("BalanceReturned");
        //details.GetValue<string>("BaseAmount");
        //details.GetValue<string>("CashbackAmount");
        //details.GetValue<string>("TaxAmount");
        //details.GetValue<string>("TipAmount");
        //details.GetValue<string>("DonationAmount");
        //details.GetValue<string>("TotalAmount");

        // TODO: Store and Forward
        /*
            <ApprovedSAFSummaryRecord>
                <NumberTransactions>[Number]</NumberTransactions>
                <TotalAmount>[Amount]</TotalAmount>
            </ApprovedSAFSummaryRecord>
            <PendingSAFSummaryRecord>
                <NumberTransactions>[Number]</NumberTransactions>
                <TotalAmount>[Amount]</TotalAmount>
            </PendingSAFSummaryRecord>
            <DeclinedSAFSummaryRecord>
                <NumberTransactions>[Number]</NumberTransactions>
                <TotalAmount>[Amount]</TotalAmount>
            </DeclinedSAFSummaryRecord>
        */

        Element[] safRecords = response.getAll("ApprovedSAFRecord");
        for(Element record: safRecords) {
            /*
                <ApprovedSAFRecord>
                <ReferenceNumber>[Number]</ ReferenceNumber>
                <TransactionTime>[Number]</ TransactionTime>
                <MaskedPAN>[Number]</ MaskedPAN>
                <CardType>[Card Type]</CardType>
                <TransactionType>[Transaction Type]</TransactionType>
                <CardAcquisition>[Card Acquisition]</CardAcquisition>
                <ApprovalCode>[Code]</ApprovalCode>
                <BaseAmount>[Amount]</BaseAmount>
                <TaxAmount>[Amount]</TaxAmount>
                <TaxAmount>[Amount]</TaxAmount>
                <TipAmount>[Amount]</TipAmount>
                <TotalAmount>[Amount]</TotalAmount>
                </ApprovedSAFRecord>
            */
        }
    }
}
