package com.global.api.terminals.builders;

import com.global.api.ServicesContainer;
import com.global.api.entities.Address;
import com.global.api.entities.enums.CurrencyType;
import com.global.api.entities.enums.PaymentMethodType;
import com.global.api.entities.enums.TaxType;
import com.global.api.entities.enums.TransactionType;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.paymentMethods.CreditCardData;
import com.global.api.paymentMethods.IPaymentMethod;
import com.global.api.paymentMethods.TransactionReference;
import com.global.api.terminals.DeviceController;
import com.global.api.terminals.TerminalResponse;

import java.math.BigDecimal;
import java.util.EnumSet;

public class TerminalAuthBuilder extends TerminalBuilder<TerminalAuthBuilder> {
    private Address address;
    private boolean allowDuplicates;
    private BigDecimal amount;
    private String authCode;
    private CurrencyType currency;
    private String customerCode;
    private BigDecimal gratuity;
    private String invoiceNumber;
    private String poNumber;
    private boolean requestMultiUseToken;
    private boolean signatureCapture;
    private BigDecimal taxAmount;
    private String taxExempt;
    private String taxExemptId;
    private String transactionId;

    public Address getAddress() {
        return address;
    }
    public boolean isAllowDuplicates() {
        return allowDuplicates;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getAuthCode() {
        if(paymentMethod instanceof TransactionReference)
            return ((TransactionReference)paymentMethod).getAuthCode();
        return null;
    }
    public CurrencyType getCurrency() {
        return currency;
    }
    public BigDecimal getGratuity() {
        return gratuity;
    }
    public String getInvoiceNumber() {
        return invoiceNumber;
    }
    public boolean isRequestMultiUseToken() {
        return requestMultiUseToken;
    }
    public boolean isSignatureCapture() {
        return signatureCapture;
    }
    public String getTransactionId() {
        if(paymentMethod instanceof TransactionReference)
            ((TransactionReference)paymentMethod).getTransactionId();
        return null;
    }
    public String getCustomerCode() {
        return customerCode;
    }
    public String getPoNumber() {
        return poNumber;
    }
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }
    public String getTaxExempt() {
        return taxExempt;
    }
    public String getTaxExemptId() {
        return taxExemptId;
    }

    public TerminalAuthBuilder withAddress(Address address) {
        this.address = address;
        return this;
    }
    public TerminalAuthBuilder withAllowDuplicates(boolean allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
        return this;
    }
    public TerminalAuthBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }
    public TerminalAuthBuilder withAuthCode(String value) {
        if (paymentMethod == null || !(paymentMethod instanceof TransactionReference))
            paymentMethod = new TransactionReference();
        ((TransactionReference)paymentMethod).setAuthCode(value);
        this.authCode = value;
        return this;
    }
    public TerminalAuthBuilder withCurrency(CurrencyType value) {
        this.currency = value;
        return this;
    }
    public TerminalAuthBuilder withCustomerCode(String value) {
        this.customerCode = value;
        return this;
    }
    public TerminalAuthBuilder withGratuity(BigDecimal gratuity) {
        this.gratuity = gratuity;
        return this;
    }
    public TerminalAuthBuilder withInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
        return this;
    }
    public TerminalAuthBuilder withPaymentMethod(IPaymentMethod method) {
        paymentMethod = method;
        return this;
    }
    public TerminalAuthBuilder withPoNumber(String value) {
        this.poNumber = value;
        return this;
    }
    public TerminalAuthBuilder withRequestMultiUseToken(boolean requestMultiUseToken) {
        this.requestMultiUseToken = requestMultiUseToken;
        return this;
    }
    public TerminalAuthBuilder withSignatureCapture(boolean signatureCapture) {
        this.signatureCapture = signatureCapture;
        return this;
    }
    public TerminalAuthBuilder withTaxAmount(BigDecimal value) {
        this.taxAmount = value;
        return this;
    }
    public TerminalAuthBuilder withTaxType(TaxType value) {
        return withTaxType(value, null);
    }
    public TerminalAuthBuilder withTaxType(TaxType value, String taxExemptId) {
        this.taxExempt = (value.equals(TaxType.TaxExempt)) ? "1" : "0";
        this.taxExemptId = taxExemptId;
        return this;
    }
    public TerminalAuthBuilder withToken(String value) {
        if (paymentMethod == null || !(paymentMethod instanceof CreditCardData))
            paymentMethod = new CreditCardData();
        ((CreditCardData)paymentMethod).setToken(value);
        return this;
    }
    public TerminalAuthBuilder withTransactionId(String value) {
        if (paymentMethod == null || !(paymentMethod instanceof TransactionReference))
            paymentMethod = new TransactionReference();
        ((TransactionReference)paymentMethod).setTransactionId(value);
        this.transactionId = value;
        return this;
    }

    public TerminalAuthBuilder(TransactionType type, PaymentMethodType paymentType) {
        super(type, paymentType);
    }

    public TerminalResponse execute() throws ApiException {
        super.execute();

        DeviceController device = ServicesContainer.getInstance().getDeviceController();
        return device.processTransaction(this);
    }

    public void setupValidations() {
        this.validations.of(EnumSet.of(TransactionType.Sale, TransactionType.Auth)).check("amount").isNotNull();
        this.validations.of(TransactionType.Refund).check("amount").isNotNull();
        this.validations.of(TransactionType.Auth)
                .with(PaymentMethodType.Credit)
                .when("transactionId").isNotNull()
                .check("authCode").isNotNull();
        this.validations.of(TransactionType.Refund)
                .with(PaymentMethodType.Credit)
                .when("transactionId").isNotNull()
                .check("authCode").isNotNull();
        this.validations.of(PaymentMethodType.Gift).check("currency").isNotNull();
        this.validations.of(TransactionType.AddValue).check("amount").isNotNull();
    }
}
