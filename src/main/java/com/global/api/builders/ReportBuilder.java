package com.global.api.builders;

import com.global.api.ServicesContainer;
import com.global.api.entities.enums.ReportType;
import com.global.api.entities.enums.TimeZoneConversion;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.gateways.IPaymentGateway;

public abstract class ReportBuilder<TResult> extends BaseBuilder<TResult> {
    private ReportType reportType;
    private TimeZoneConversion timeZoneConversion;
    private Class<TResult> clazz;

    public ReportType getReportType() {
        return reportType;
    }
    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }
    public TimeZoneConversion getTimeZoneConversion() {
        return timeZoneConversion;
    }
    public void setTimeZoneConversion(TimeZoneConversion timeZoneConversion) {
        this.timeZoneConversion = timeZoneConversion;
    }

    public ReportBuilder(ReportType type, Class<TResult> clazz) {
        super();
        this.reportType = type;
        this.clazz = clazz;
    }

    public TResult execute() throws ApiException {
        super.execute();

        IPaymentGateway client = ServicesContainer.getInstance().getGateway();
        return client.processReport(this, clazz);
    }
}
