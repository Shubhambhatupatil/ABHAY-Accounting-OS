package com.anvritai.abhay.api;

import com.anvritai.abhay.api.GstDtos.DraftReturnRequest;
import com.anvritai.abhay.api.GstDtos.GstAlertResponse;
import com.anvritai.abhay.api.GstDtos.GstDashboardResponse;
import com.anvritai.abhay.api.GstDtos.GstPeriodSummary;
import com.anvritai.abhay.api.GstDtos.GstRateRequest;
import com.anvritai.abhay.api.GstDtos.GstRateResponse;
import com.anvritai.abhay.api.GstDtos.GstRegisterResponse;
import com.anvritai.abhay.api.GstDtos.GstReturnResponse;
import com.anvritai.abhay.api.GstDtos.GstRuleRequest;
import com.anvritai.abhay.api.GstDtos.GstRuleResponse;
import com.anvritai.abhay.api.GstDtos.GstSummaryResponse;
import com.anvritai.abhay.api.GstDtos.GstinValidationRequest;
import com.anvritai.abhay.api.GstDtos.GstinValidationResponse;
import com.anvritai.abhay.api.GstDtos.HsnSacRequest;
import com.anvritai.abhay.api.GstDtos.HsnSacResponse;
import com.anvritai.abhay.api.GstDtos.InvoiceGstValidationResponse;
import com.anvritai.abhay.domain.gst.GstReturnStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.GstMasterService;
import com.anvritai.abhay.service.GstReportService;
import com.anvritai.abhay.service.GstReturnService;
import com.anvritai.abhay.service.GstValidationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/gst")
public class GstController {
    private final GstMasterService masters;
    private final GstValidationService validation;
    private final GstReportService reports;
    private final GstReturnService returns;

    public GstController(
            GstMasterService masters, GstValidationService validation,
            GstReportService reports, GstReturnService returns) {
        this.masters = masters;
        this.validation = validation;
        this.reports = reports;
        this.returns = returns;
    }

    @GetMapping("/rates")
    public List<GstRateResponse> rates(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return masters.listRates(principal.id(), companyId);
    }

    @PostMapping("/rates")
    @ResponseStatus(HttpStatus.CREATED)
    public GstRateResponse createRate(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody GstRateRequest request) {
        return masters.createRate(principal.id(), companyId, request);
    }

    @PatchMapping("/rates/{rateId}")
    public GstRateResponse updateRate(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID rateId, @Valid @RequestBody GstRateRequest request) {
        return masters.updateRate(principal.id(), companyId, rateId, request);
    }

    @DeleteMapping("/rates/{rateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateRate(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID rateId) {
        masters.deactivateRate(principal.id(), companyId, rateId);
    }

    @GetMapping("/hsn-sac")
    public List<HsnSacResponse> searchHsnSac(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam(required = false) String search) {
        return masters.searchHsnSac(principal.id(), companyId, search);
    }

    @PostMapping("/hsn-sac")
    @ResponseStatus(HttpStatus.CREATED)
    public HsnSacResponse createHsnSac(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody HsnSacRequest request) {
        return masters.createHsnSac(principal.id(), companyId, request);
    }

    @PatchMapping("/hsn-sac/{codeId}")
    public HsnSacResponse updateHsnSac(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID codeId, @Valid @RequestBody HsnSacRequest request) {
        return masters.updateHsnSac(principal.id(), companyId, codeId, request);
    }

    @DeleteMapping("/hsn-sac/{codeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateHsnSac(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID codeId) {
        masters.deactivateHsnSac(principal.id(), companyId, codeId);
    }

    @GetMapping("/rules")
    public List<GstRuleResponse> rules(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return masters.listRules(principal.id(), companyId);
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public GstRuleResponse createRule(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody GstRuleRequest request) {
        return masters.createRule(principal.id(), companyId, request);
    }

    @PatchMapping("/rules/{ruleId}")
    public GstRuleResponse updateRule(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID ruleId, @Valid @RequestBody GstRuleRequest request) {
        return masters.updateRule(principal.id(), companyId, ruleId, request);
    }

    @DeleteMapping("/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateRule(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID ruleId) {
        masters.deactivateRule(principal.id(), companyId, ruleId);
    }

    @PostMapping("/validate-gstin")
    public GstinValidationResponse validateGstin(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody GstinValidationRequest request) {
        masters.listRates(principal.id(), companyId);
        return validation.validateGstin(request.gstin(), request.expectedStateCode());
    }

    @GetMapping("/invoices/{invoiceId}/validation")
    public InvoiceGstValidationResponse validateInvoice(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {
        return validation.validateInvoice(principal.id(), companyId, invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/scan")
    public InvoiceGstValidationResponse scanInvoice(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {
        return validation.scanInvoice(principal.id(), companyId, invoiceId);
    }

    @GetMapping("/alerts")
    public List<GstAlertResponse> alerts(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return validation.listAlerts(principal.id(), companyId);
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public GstAlertResponse resolveAlert(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID alertId) {
        return validation.resolveAlert(principal.id(), companyId, alertId);
    }

    @GetMapping("/reports/summary")
    public GstSummaryResponse summary(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam(name = "date_from") LocalDate from,
            @RequestParam(name = "date_to") LocalDate to) {
        return reports.summary(principal.id(), companyId, from, to);
    }

    @GetMapping("/reports/sales-register")
    public GstRegisterResponse salesRegister(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam(name = "date_from") LocalDate from,
            @RequestParam(name = "date_to") LocalDate to) {
        return reports.register(principal.id(), companyId, InvoiceType.SALES, from, to);
    }

    @GetMapping("/reports/purchase-register")
    public GstRegisterResponse purchaseRegister(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam(name = "date_from") LocalDate from,
            @RequestParam(name = "date_to") LocalDate to) {
        return reports.register(principal.id(), companyId, InvoiceType.PURCHASE, from, to);
    }

    @GetMapping("/reports/input-credit")
    public GstSummaryResponse inputCredit(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam(name = "date_from") LocalDate from,
            @RequestParam(name = "date_to") LocalDate to) {
        return reports.summary(principal.id(), companyId, from, to);
    }

    @GetMapping("/reports/output-liability")
    public GstSummaryResponse outputLiability(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam(name = "date_from") LocalDate from,
            @RequestParam(name = "date_to") LocalDate to) {
        return reports.summary(principal.id(), companyId, from, to);
    }

    @GetMapping("/reports/monthly")
    public List<GstPeriodSummary> monthly(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam int year) {
        return reports.monthly(principal.id(), companyId, year);
    }

    @GetMapping("/reports/quarterly")
    public List<GstPeriodSummary> quarterly(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam int year) {
        return reports.quarterly(principal.id(), companyId, year);
    }

    @GetMapping("/reports/yearly")
    public GstPeriodSummary yearly(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam int year) {
        return reports.yearly(principal.id(), companyId, year);
    }

    @GetMapping("/dashboard")
    public GstDashboardResponse dashboard(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return reports.dashboard(principal.id(), companyId);
    }

    @PostMapping("/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public GstReturnResponse generateReturn(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody DraftReturnRequest request) {
        return returns.generate(principal.id(), companyId, request);
    }

    @GetMapping("/returns")
    public List<GstReturnResponse> returns(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return returns.list(principal.id(), companyId);
    }

    @GetMapping("/returns/{returnId}")
    public GstReturnResponse gstReturn(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID returnId) {
        return returns.get(principal.id(), companyId, returnId);
    }

    @PostMapping("/returns/{returnId}/finalize")
    public GstReturnResponse finalizeReturn(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID returnId) {
        return returns.setStatus(principal.id(), companyId, returnId, GstReturnStatus.FINALIZED);
    }

    @PostMapping("/returns/{returnId}/cancel")
    public GstReturnResponse cancelReturn(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID returnId) {
        return returns.setStatus(principal.id(), companyId, returnId, GstReturnStatus.CANCELLED);
    }

    @GetMapping(value = "/returns/{returnId}/csv", produces = "text/csv")
    public ResponseEntity<String> returnCsv(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID returnId) {
        GstReturnResponse gstReturn = returns.get(principal.id(), companyId, returnId);
        String filename = gstReturn.returnType().name().toLowerCase() + "-draft.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(returns.csv(principal.id(), companyId, returnId));
    }
}
