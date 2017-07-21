package com.global.api.gateways;

import com.global.api.builders.*;
import com.global.api.entities.*;
import com.global.api.entities.enums.*;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.exceptions.GatewayException;
import com.global.api.entities.exceptions.UnsupportedTransactionException;
import com.global.api.paymentMethods.*;
import com.global.api.utils.Element;
import com.global.api.utils.ElementTree;
import com.global.api.utils.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PorticoConnector extends XmlGateway implements IPaymentGateway {
    private int siteId;
    private int licenseId;
    private int deviceId;
    private String username;
    private String password;
    private String developerId;
    private String versionNumber;
    private String secretApiKey;

    public boolean supportsHostedPayments() { return false; }

    public void setSiteId(int siteId) {
        this.siteId = siteId;
    }
    public void setLicenseId(int licenseId) {
        this.licenseId = licenseId;
    }
    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
    }
    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }
    public void setSecretApiKey(String secretApiKey) {
        this.secretApiKey = secretApiKey;
    }

    public Transaction processAuthorization(AuthorizationBuilder builder) throws ApiException {
        ElementTree et = new ElementTree();
        TransactionType type = builder.getTransactionType();
        TransactionModifier modifier = builder.getTransactionModifier();
        PaymentMethodType paymentType = builder.getPaymentMethod().getPaymentMethodType();

        // build request
        Element transaction = et.element(mapTransactionType(builder));
        Element block1 = et.subElement(transaction, "Block1");
        if (type.equals(TransactionType.Sale) || type.equals(TransactionType.Auth)) {
            if (paymentType != PaymentMethodType.Gift && paymentType != PaymentMethodType.ACH) {
                et.subElement(block1, "AllowDup", builder.isAllowDuplicates() ? "Y" : "N");
                if (modifier.equals(TransactionModifier.None) && paymentType != PaymentMethodType.EBT && paymentType != PaymentMethodType.Recurring)
                    et.subElement(block1, "AllowPartialAuth", builder.isAllowPartialAuth() ? "Y" : "N");
            }
        }
        et.subElement(block1, "Amt", builder.getAmount());
        et.subElement(block1, "GratuityAmtInfo", builder.getGratuity());

        // because plano...
        et.subElement(block1, paymentType == PaymentMethodType.Debit ? "CashbackAmtInfo" : "CashBackAmount", builder.getCashBackAmount());

        // offline auth code
        et.subElement(block1, "OfflineAuthCode", builder.getOfflineAuthCode());

        // alias action
        if (type.equals(TransactionType.Alias)) {
            et.subElement(block1, "Action").text(builder.getAliasAction());
            et.subElement(block1, "Alias").text(builder.getAlias());
        }

        boolean isCheck = (paymentType.equals(PaymentMethodType.ACH));
        if (isCheck || builder.getBillingAddress() != null) {
            Element holder = et.subElement(block1, isCheck ? "ConsumerInfo" : "CardHolderData");

            Address address = builder.getBillingAddress();
            if (address != null) {
                et.subElement(holder, isCheck ? "Address1" : "CardHolderAddr", address.getStreetAddress1());
                et.subElement(holder, isCheck ? "City" : "CardHolderCity", address.getCity());
                et.subElement(holder, isCheck ? "State" : "CardHolderState", address.getProvince());
                et.subElement(holder, isCheck ? "Zip" : "CardHolderZip", address.getPostalCode());
            }

            if (isCheck) {
                eCheck check = (eCheck)builder.getPaymentMethod();
                if (!StringUtils.isNullOrEmpty(check.getCheckHolderName())) {
                    String[] names = check.getCheckHolderName().split(" ", 2);
                    et.subElement(holder, "FirstName", names[0]);
                    et.subElement(holder, "LastName", names[1]);
                }
                et.subElement(holder, "CheckName", check.getCheckName());
                et.subElement(holder, "PhoneNumber", check.getPhoneNumber());
                et.subElement(holder, "DLNumber", check.getDriversLicenseNumber());
                et.subElement(holder, "DLState", check.getDriversLicenseState());

                if (!StringUtils.isNullOrEmpty(check.getSsnLast4()) || check.getBirthYear() != 0) {
                    Element identity = et.subElement(holder, "IdentityInfo");
                    et.subElement(identity, "SSNL4", check.getSsnLast4());
                    et.subElement(identity, "DOBYear", check.getBirthYear());
                }
            }
            else {
                // TODO: card holder name
            }
        }

        // card data
        String tokenValue = getToken(builder.getPaymentMethod());
        boolean hasToken = !StringUtils.isNullOrEmpty(tokenValue);

        // because debit is weird (Ach too)
        Element cardData = null;
        if (paymentType.equals(PaymentMethodType.Debit) || paymentType.equals(PaymentMethodType.ACH))
            cardData = block1;
        else cardData = et.element("CardData");

        if (builder.getPaymentMethod() instanceof ICardData) {
            ICardData card = (ICardData) builder.getPaymentMethod();

            Element manualEntry = et.subElement(cardData, hasToken ? "TokenData" : "ManualEntry");
            et.subElement(manualEntry, hasToken ? "TokenValue" : "CardNbr").text(tokenValue != null ? tokenValue : card.getNumber());
            et.subElement(manualEntry, "ExpMonth").text(card.getExpMonth().toString());
            et.subElement(manualEntry, "ExpYear").text(card.getExpYear().toString());
            et.subElement(manualEntry, "CVV2", card.getCvn());
            et.subElement(manualEntry, "ReaderPresent", card.isReaderPresent() ? "Y" : "N");
            et.subElement(manualEntry, "CardPresent", card.isCardPresent() ? "Y" : "N");
            block1.append(cardData);

            // recurring data
            if(builder.getTransactionModifier().equals(TransactionModifier.Recurring)) {
                Element recurring = et.subElement(block1, "RecurringData");
                et.subElement(recurring, "ScheduleID", builder.getScheduleId());
                et.subElement(recurring, "OneTime").text(builder.isOneTimePayment() ? "Y" : "N");
            }
        }
        else if (builder.getPaymentMethod() instanceof ITrackData) {
            ITrackData track = (ITrackData)builder.getPaymentMethod();

            Element trackData = et.subElement(cardData, hasToken ? "TokenData" : "TrackData");
            if (!hasToken) {
                trackData.text(track.getValue());
                if (paymentType != PaymentMethodType.Debit) {
                    trackData.set("method", track.getEntryMethod());
                    block1.append(cardData);
                }
            }
            else et.subElement(trackData, "TokenValue").text(tokenValue);
        }
        else if (builder.getPaymentMethod() instanceof GiftCard) {
            GiftCard card = (GiftCard)builder.getPaymentMethod();

            // currency
            et.subElement(block1, "Currency", builder.getCurrency());

            // if it's replace add the new card and change the card data name to be old card data
            if (type.equals(TransactionType.Replace)) {
                GiftCard replacement = builder.getReplacementCard();
                Element newCardData = et.subElement(block1, "NewCardData");
                et.subElement(newCardData, replacement.getValueType(), replacement.getValue());
                et.subElement(newCardData, "PIN", replacement.getPin());

                cardData = et.element("OldCardData");
            }
            et.subElement(cardData, card.getValueType(), card.getValue());
            et.subElement(cardData, "PIN", card.getPin());

            if (builder.getAliasAction() != AliasAction.Create)
                block1.append(cardData);
        }
        else if (builder.getPaymentMethod() instanceof eCheck) {
            eCheck check = (eCheck)builder.getPaymentMethod();

            // check action
            et.subElement(block1, "CheckAction").text("SALE");

            // account info
            if (StringUtils.isNullOrEmpty(check.getToken())) {
                Element accountInfo = et.subElement(block1, "AccountInfo");
                et.subElement(accountInfo, "RoutingNumber", check.getRoutingNumber());
                et.subElement(accountInfo, "AccountNumber", check.getAccountNumber());
                et.subElement(accountInfo, "CheckNumber", check.getCheckNumber());
                et.subElement(accountInfo, "MICRData", check.getMicrNumber());
                et.subElement(accountInfo, "AccountType", check.getAccountType());
            }
            else et.subElement(block1, "TokenValue").text(tokenValue);

            et.subElement(block1, "DataEntryMode", check.getEntryMode().getValue().toUpperCase());
            et.subElement(block1, "CheckType", check.getCheckType());
            et.subElement(block1, "SECCode", check.getSecCode());

            // verify info
            Element verify = et.subElement(block1, "VerifyInfo");
            et.subElement(verify, "CheckVerify").text(check.isCheckVerify() ? "Y" : "N");
            et.subElement(verify, "ACHVerify").text(check.isAchVerify() ? "Y" : "N");
        }
        else if(builder.getPaymentMethod() instanceof TransactionReference) {
            TransactionReference reference = (TransactionReference)builder.getPaymentMethod();
            et.subElement(block1, "GatewayTxnId", reference.getTransactionId());
            et.subElement(block1, "ClientTxnId", reference.getClientTransactionId());
        }
        else if(builder.getPaymentMethod() instanceof RecurringPaymentMethod) {
            RecurringPaymentMethod method = (RecurringPaymentMethod)builder.getPaymentMethod();

            // check action
            if(method.getPaymentType().equals("ACH")) {
                block1.remove("AllowDup");
                et.subElement(block1, "CheckAction").text("SALE");
            }

            // payment method stuff
            et.subElement(block1, "PaymentMethodKey").text(method.getKey());
            if(method.getPaymentMethod() != null && method.getPaymentMethod() instanceof CreditCardData) {
                CreditCardData card = (CreditCardData)method.getPaymentMethod();
                Element data = et.subElement(block1, "PaymentMethodKeyData");
                et.subElement(data, "ExpMonth", card.getExpMonth());
                et.subElement(data, "ExpYear", card.getExpYear());
                et.subElement(data, "CVV2", card.getCvn());
            }

            // recurring data
            Element recurring = et.subElement(block1, "RecurringData");
            et.subElement(recurring, "ScheduleID", builder.getScheduleId());
            et.subElement(recurring, "OneTime").text(builder.isOneTimePayment() ? "Y" : "N");
        }

        // pin block
        if (builder.getPaymentMethod() instanceof IPinProtected) {
            if(!type.equals(TransactionType.Reversal))
                et.subElement(block1, "PinBlock", ((IPinProtected)builder.getPaymentMethod()).getPinBlock());
        }

        // encryption
        if (builder.getPaymentMethod() instanceof IEncryptable) {
            EncryptionData encryptionData = ((IEncryptable)builder.getPaymentMethod()).getEncryptionData();

            if (encryptionData != null) {
                Element enc = et.subElement(cardData, "EncryptionData");
                et.subElement(enc, "Version").text(encryptionData.getVersion());
                et.subElement(enc, "EncryptedTrackNumber", encryptionData.getTrackNumber());
                et.subElement(enc, "KTB", encryptionData.getKtb());
                et.subElement(enc, "KSN", encryptionData.getKsn());
            }
        }

        // set token flag
        if (builder.getPaymentMethod() instanceof ITokenizable) {
            et.subElement(cardData, "TokenRequest").text(builder.isRequestMultiUseToken() ? "Y" : "N");
        }

        // balance inquiry type
        if (builder.getPaymentMethod() instanceof IBalanceable)
        et.subElement(block1, "BalanceInquiryType", builder.getBalanceInquiryType());

        // cpc request
        if (builder.isLevel2Request())
            et.subElement(block1, "CPCReq", "Y");

        // details
        if (!StringUtils.isNullOrEmpty(builder.getCustomerId()) || !StringUtils.isNullOrEmpty(builder.getDescription()) || !StringUtils.isNullOrEmpty(builder.getInvoiceNumber())) {
            Element addons = et.subElement(block1, "AdditionalTxnFields");
            et.subElement(addons, "CustomerID", builder.getCustomerId());
            et.subElement(addons, "Description", builder.getDescription());
            et.subElement(addons, "InvoiceNbr", builder.getInvoiceNumber());
        }

        // ecommerce info
        if(builder.getEcommerceInfo() != null) {
            EcommerceInfo ecom = builder.getEcommerceInfo();
            et.subElement(block1, "Ecommerce", ecom.getChannel());
            if(!StringUtils.isNullOrEmpty(builder.getInvoiceNumber()) || ecom.getShipMonth() != null) {
                Element direct = et.subElement(block1, "DirectMktData");
                et.subElement(direct, "DirectMktInvoiceNbr").text(builder.getInvoiceNumber());
                et.subElement(direct, "DirectMktShipDay").text(ecom.getShipDay().toString());
                et.subElement(direct, "DirectMktShipMonth").text(ecom.getShipMonth().toString());
            }

            if(!StringUtils.isNullOrEmpty(ecom.getCavv()) || !StringUtils.isNullOrEmpty(ecom.getEci()) || !StringUtils.isNullOrEmpty(ecom.getXid())) {
                Element secureEcommerce = et.subElement(block1, "SecureECommerce");
                et.subElement(secureEcommerce, "PaymentDataSource", ecom.getPaymentDataSource());
                et.subElement(secureEcommerce, "TypeOfPaymentData", ecom.getPaymentDataType());
                et.subElement(secureEcommerce, "PaymentData", ecom.getCavv());
                et.subElement(secureEcommerce, "ECommerceIndicator", ecom.getEci());
                et.subElement(secureEcommerce, "XID", ecom.getXid());
            }
        }

        // dynamic descriptor
        et.subElement(block1, "TxnDescriptor", builder.getDynamicDescriptor());

        // auto substantiation

        String response = doTransaction(buildEnvelope(et, transaction, builder.getClientTransactionId()));
        return mapResponse(response, builder.getPaymentMethod());
    }

    public String serializeRequest(AuthorizationBuilder builder) throws ApiException {
        throw new UnsupportedTransactionException("Portico does not support hosted payments.");
    }

    public Transaction manageTransaction(ManagementBuilder builder) throws ApiException {
        ElementTree et = new ElementTree();
        TransactionType type = builder.getTransactionType();
        TransactionModifier modifier = builder.getTransactionModifier();

        // build request
        Element transaction = et.element(mapTransactionType(builder));

        if (!type.equals(TransactionType.BatchClose)) {
            PaymentMethodType paymentType = builder.getPaymentMethod().getPaymentMethodType();

            Element root;
            if (type.equals(TransactionType.Reversal)
                    || type.equals(TransactionType.Refund)
                    || paymentType.equals(PaymentMethodType.Gift)
                    || paymentType.equals(PaymentMethodType.ACH))
                root = et.subElement(transaction, "Block1");
            else root = transaction;

            // amount
            if (builder.getAmount() != null)
                et.subElement(root, "Amt").text(builder.getAmount().toString());

            // gratuity
            if (builder.getGratuity() != null)
                et.subElement(root, "GratuityAmtInfo").text(builder.getGratuity().toString());

            // Transaction ID
            et.subElement(root, "GatewayTxnId", builder.getTransactionId());

            // client transaction id
            if(builder.getTransactionType().equals(TransactionType.Reversal))
                et.subElement(root, "ClientTxnId", builder.getClientTransactionId());

            // Level II Data
            if (type.equals(TransactionType.Edit) && modifier.equals(TransactionModifier.LevelII)) {
                Element cpc = et.subElement(root, "CPCData");
                et.subElement(cpc, "CardHolderPONbr", builder.getPoNumber());
                et.subElement(cpc, "TaxType", builder.getTaxType());
                et.subElement(cpc, "TaxAmt", builder.getTaxAmount());
            }
        }

        String response = doTransaction(buildEnvelope(et, transaction, builder.getClientTransactionId()));
        return mapResponse(response, builder.getPaymentMethod());
    }

    public <TResult> TResult processReport(ReportBuilder<TResult> builder, Class<TResult> clazz) throws ApiException {
        ElementTree et = new ElementTree();

        Element transaction = et.element(mapReportType(builder.getReportType()));
        et.subElement(transaction, "TzConversion", builder.getTimeZoneConversion());
        if(builder instanceof TransactionReportBuilder) {
            TransactionReportBuilder<TResult> trb = (TransactionReportBuilder<TResult>)builder;
            et.subElement(transaction, "DeviceId", trb.getDeviceId());
            if(trb.getStartDate() != null)
                et.subElement(transaction, "RptStartUtcDT", formatDate(trb.getStartDate()));
            if(trb.getEndDate() != null)
                et.subElement(transaction, "RptEndUtcDT", formatDate(trb.getStartDate()));
            et.subElement(transaction, "TxnId", trb.getTransactionId());
        }

        String response = doTransaction(buildEnvelope(et, transaction));
        return mapReportResponse(response, builder.getReportType(), clazz);
    }

    private String buildEnvelope(ElementTree et, Element transaction) {
        return buildEnvelope(et, transaction, null);
    }
    private String buildEnvelope(ElementTree et, Element transaction, String clientTransactionId) {
        Element envelope = et.element("soap:Envelope");
        envelope.set("xmlns:soap", "http://schemas.xmlsoap.org/soap/envelope/");
        envelope.set("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        envelope.set("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");

        Element body = et.subElement(envelope, "soap:Body");
        Element request = et.subElement(body, "PosRequest").set("xmlns", "http://Hps.Exchange.PosGateway");
        Element version1 = et.subElement(request, "Ver1.0");

        // header
        Element header = et.subElement(version1, "Header");
        et.subElement(header, "SecretAPIKey", secretApiKey);
        et.subElement(header, "SiteId", siteId);
        et.subElement(header, "LicenseId", licenseId);
        et.subElement(header, "DeviceId", deviceId);
        et.subElement(header, "UserName", username);
        et.subElement(header, "Password", password);
        et.subElement(header, "DeveloperId", developerId);
        et.subElement(header, "VersionNumber", versionNumber);
        et.subElement(header, "ClientTxnId", clientTransactionId);

        // Transaction
        Element trans = et.subElement(version1, "Transaction");
        trans.append(transaction);

        return et.toString(envelope);
    }

    private Transaction mapResponse(String rawResponse, IPaymentMethod paymentMethod) throws ApiException {
        Transaction result = new Transaction();

        Element root = ElementTree.parse(rawResponse).get("PosResponse");
        ArrayList<String> acceptedCodes = new ArrayList<String>();
        acceptedCodes.add("00");
        acceptedCodes.add("0");
        acceptedCodes.add("85");
        acceptedCodes.add("10");

        // check gateway responses
        String gatewayRspCode = normalizeResponse(root.getString("GatewayRspCode"));
        String gatewayRspText = root.getString("GatewayRspMsg");

        if (!acceptedCodes.contains(gatewayRspCode)) {
            throw new GatewayException(
                    String.format("Unexpected Gateway Response: %s - %s", gatewayRspCode, gatewayRspText),
                    gatewayRspCode,
                    gatewayRspText
            );
        }
        else {
            result.setAuthorizedAmount(root.getDecimal("AuthAmt"));
            result.setAvailableBalance(root.getDecimal("AvailableBalance"));
            result.setAvsResponseCode(root.getString("AVSRsltCode"));
            result.setAvsResponseMessage(root.getString("AVSRsltText"));
            result.setBalanceAmount(root.getDecimal("BalanceAmt"));
            result.setCardType(root.getString("CardType"));
            result.setCardLast4(root.getString("TokenPANLast4"));
            result.setCavvResponseCode(root.getString("CAVVResultCode"));
            result.setCommercialIndicator(root.getString("CPCInd"));
            result.setCvnResponseCode(root.getString("CVVRsltCode"));
            result.setCvnResponseMessage(root.getString("CVVRsltText"));
            result.setEmvIssuerResponse(root.getString("EMVIssuerResp"));
            result.setPointsBalanceAmount(root.getDecimal("PointsBalanceAmt"));
            result.setRecurringDataCode(root.getString("RecurringDataCode"));
            result.setReferenceNumber(root.getString("RefNbr"));

            String responseCode = normalizeResponse(root.getString("RspCode"));
            String responseText = root.getString("RspText", "RspMessage");
            result.setResponseCode(responseCode != null ? responseCode : gatewayRspCode);
            result.setResponseMessage(responseText != null ? responseText : gatewayRspText);
            result.setTransactionDescriptor(root.getString("TxnDescriptor"));

            if (paymentMethod != null) {
                TransactionReference reference = new TransactionReference();
                reference.setPaymentMethodType(paymentMethod.getPaymentMethodType());
                reference.setTransactionId(root.getString("GatewayTxnId"));
                reference.setAuthCode(root.getString("AuthCode"));
                result.setTransactionReference(reference);
            }

            // gift card create data
            if (root.has("CardData")) {
                GiftCard giftCard = new GiftCard();
                giftCard.setNumber(root.getString("CardNbr"));
                giftCard.setAlias(root.getString("Alias"));
                giftCard.setPin(root.getString("PIN"));

                result.setGiftCard(giftCard);
            }

            // token data
            if(root.has("TokenData")) {
                result.setToken(root.getString("TokenValue"));
            }

            // batch information
            if(root.has("BatchId")) {
                BatchSummary summary = new BatchSummary();
                summary.setBatchId(root.getInt("BatchId"));
                summary.setTransactionCount(root.getInt("TxnCnt"));
                summary.setTotalAmount(root.getDecimal("TotalAmt"));
                summary.setSequenceNumber(root.getString("BatchSeqNbr"));
                result.setBatchSummary(summary);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <TResult> TResult mapReportResponse(String rawResponse, ReportType reportType, Class<TResult> clazz) throws ApiException {
        Element doc = ElementTree.parse(rawResponse).get(mapReportType(reportType));

        try {
            TResult rvalue = clazz.newInstance();
            if(reportType.equals(ReportType.Activity) || reportType.equals(ReportType.TransactionDetail)) {
                // Activity
                if (rvalue instanceof ActivityReport){
                    ActivityReport list = new ActivityReport();
                    for(Element detail: doc.getAll("Details")) {
                        list.add(hydrateTransactionSummary(detail));
                    }
                }
                else{
                    rvalue = (TResult) hydrateTransactionSummary(doc);
                }
            }
            return rvalue;
        }
        catch(Exception e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private String normalizeResponse(String input) {
        if(input != null) {
            if (input.equals("0") || input.equals("85"))
                return "00";
        }
        return input;
    }

    private <T extends TransactionBuilder<Transaction>> String mapTransactionType(T builder) throws ApiException {
        TransactionModifier modifier = builder.getTransactionModifier();
        PaymentMethodType paymentMethodType = null;
        if(builder.getPaymentMethod() != null)
            paymentMethodType = builder.getPaymentMethod().getPaymentMethodType();

        switch(builder.getTransactionType()) {
            case BatchClose:
                return "BatchClose";
            case Decline:
                if(modifier.equals(TransactionModifier.ChipDecline))
                    return "ChipCardDecline";
                else if(modifier.equals(TransactionModifier.FraudDecline))
                    return "OverrideFraudDecline";
                throw new UnsupportedTransactionException();
            case Verify:
                return "CreditAccountVerify";
            case Capture:
                return "CreditAddToBatch";
            case Auth:
                if(paymentMethodType.equals(PaymentMethodType.Credit)) {
                    if(modifier.equals(TransactionModifier.Additional))
                        return "CreditAdditionalAuth";
                    else if(modifier.equals(TransactionModifier.Incremental))
                        return "CreditIncrementalAuth";
                    else if(modifier.equals(TransactionModifier.Offline))
                        return "CreditOfflineAuth";
                    else if(modifier.equals(TransactionModifier.Recurring))
                        return "RecurringBillingAuth";
                    return "CreditAuth";
                }
                else if(paymentMethodType.equals(PaymentMethodType.Recurring))
                    return "RecurringBillingAuth";
                throw new UnsupportedTransactionException();
            case Sale:
                if (paymentMethodType.equals(PaymentMethodType.Credit)) {
                    if (modifier.equals(TransactionModifier.Offline))
                        return "CreditOfflineSale";
                    else if(modifier.equals(TransactionModifier.Recurring))
                        return "RecurringBilling";
                    else return "CreditSale";
                }
                else if (paymentMethodType.equals(PaymentMethodType.Recurring)) {
                    if(((RecurringPaymentMethod)builder.getPaymentMethod()).getPaymentType().equals("ACH"))
                        return "CheckSale";
                    return "RecurringBilling";
                }
                else if (paymentMethodType.equals(PaymentMethodType.Debit))
                    return "DebitSale";
                else if (paymentMethodType.equals(PaymentMethodType.Cash))
                    return "CashSale";
                else if (paymentMethodType.equals(PaymentMethodType.ACH))
                    return "CheckSale";
                else if (paymentMethodType.equals(PaymentMethodType.EBT)) {
                    if (modifier.equals(TransactionModifier.CashBack))
                        return "EBTCashBackPurchase";
                    else if (modifier.equals(TransactionModifier.Voucher))
                        return "EBTVoucherPurchase";
                    else return "EBTFSPurchase";
                }
                else if (paymentMethodType.equals(PaymentMethodType.Gift))
                    return "GiftCardSale";
                throw new UnsupportedTransactionException();
            case Refund:
                if (paymentMethodType.equals(PaymentMethodType.Credit))
                    return "CreditReturn";
                else if (paymentMethodType.equals(PaymentMethodType.Debit)) {
                    if(builder.getPaymentMethod() instanceof TransactionReference)
                        throw new UnsupportedTransactionException();
                    return "DebitReturn";
                }
                else if (paymentMethodType.equals(PaymentMethodType.Cash))
                    return "CashReturn";
                else if (paymentMethodType.equals(PaymentMethodType.EBT)) {
                    if(builder.getPaymentMethod() instanceof TransactionReference)
                        throw new UnsupportedTransactionException();
                    return "EBTFSReturn";
                }
                throw new UnsupportedTransactionException();
            case Reversal:
                if (paymentMethodType.equals(PaymentMethodType.Credit))
                    return "CreditReversal";
                else if (paymentMethodType.equals(PaymentMethodType.Debit)) {
                    if(builder.getPaymentMethod() instanceof TransactionReference)
                        throw new UnsupportedTransactionException();
                    return "DebitReversal";
                }
                else if (paymentMethodType.equals(PaymentMethodType.Gift))
                    return "GiftCardReversal";
                throw new UnsupportedTransactionException();
            case Edit:
                if (modifier.equals(TransactionModifier.LevelII))
                    return "CreditCPCEdit";
                else return "CreditTxnEdit";
            case Void:
                if (paymentMethodType.equals(PaymentMethodType.Credit))
                    return "CreditVoid";
                else if (paymentMethodType.equals(PaymentMethodType.ACH))
                    return "CheckVoid";
                else if (paymentMethodType.equals(PaymentMethodType.Gift))
                    return "GiftCardVoid";
                throw new UnsupportedTransactionException();
            case AddValue:
                if (paymentMethodType.equals(PaymentMethodType.Credit))
                    return "PrePaidAddValue";
                else if (paymentMethodType.equals(PaymentMethodType.Debit))
                    return "DebitAddValue";
                else if (paymentMethodType.equals(PaymentMethodType.Gift))
                    return "GiftCardAddValue";
                throw new UnsupportedTransactionException();
            case Balance:
                if (paymentMethodType.equals(PaymentMethodType.EBT))
                    return "EBTBalanceInquiry";
                else if (paymentMethodType.equals(PaymentMethodType.Gift))
                    return "GiftCardBalance";
                else if (paymentMethodType.equals(PaymentMethodType.Credit))
                    return "PrePaidBalanceInquiry";
                throw new UnsupportedTransactionException();
            case BenefitWithdrawal:
                return "EBTCashBenefitWithdrawal";
            case Activate:
                return "GiftCardActivate";
            case Alias:
                return "GiftCardAlias";
            case Deactivate:
                return "GiftCardDeactivate";
            case Replace:
                return "GiftCardReplace";
            case Reward:
                return "GiftCardReward";
            default:
                throw new UnsupportedTransactionException();
        }
    }

    private String mapReportType(ReportType type) throws UnsupportedTransactionException {
        switch(type) {
            case Activity:
                return "ReportActivity";
            case TransactionDetail:
                return "ReportTxnDetail";
            default:
                throw new UnsupportedTransactionException();
        }
    }

    private String getToken(IPaymentMethod paymentMethod) {
        if(paymentMethod instanceof ITokenizable) {
            String tokenValue = ((ITokenizable)paymentMethod).getToken();
            if(tokenValue != null && !tokenValue.equals(""))
                return tokenValue;
            return null;
        }
        return null;
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        return sdf.format(date);
    }

    private TransactionSummary hydrateTransactionSummary(Element root) {
        TransactionSummary summary = new TransactionSummary();
        summary.setAmount(root.getDecimal("Amt"));
        summary.setAuthorizedAmount(root.getDecimal("AuthAmt"));
        summary.setAuthCode(root.getString("AuthCode"));
        summary.setClientTransactionId(root.getString("ClientTxnId"));
        summary.setDeviceId(root.getInt("DeviceId"));
        summary.setIssuerResponseCode(root.getString("IssuerRspCode", "RspCode"));
        summary.setIssuerResponseMessage(root.getString("IssuerRspText", "RspText"));
        summary.setMaskedCardNumber(root.getString("MaskedCardNbr"));
        summary.setOriginalTransactionId(root.getString("OriginalGatewayTxnId"));
        summary.setGatewayResponseCode(normalizeResponse(root.getString("GatewayRspCode")));
        summary.setGatewayResponseMessage(root.getString("GatewayRspMsg"));
        summary.setReferenceNumber(root.getString("RefNbr"));
        summary.setServiceName(root.getString("ServiceName"));
        summary.setSettlementAmount(root.getDecimal("SettlementAmt"));
        summary.setStatus(root.getString("Status", "TxnStatus"));
        summary.setTransactionDate(root.getDate("TxnUtcDT", "ReqUtcDT"));
        summary.setTransactionId(root.getString("GatewayTxnId"));

        return summary;
    }
}
