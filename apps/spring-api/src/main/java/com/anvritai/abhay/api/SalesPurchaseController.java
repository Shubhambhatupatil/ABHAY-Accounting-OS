package com.anvritai.abhay.api;

import com.anvritai.abhay.api.SalesPurchaseDtos.CustomerRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.CustomerResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceCreateRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoicePaymentRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoicePaymentResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceRegisterResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceUpdateRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.ItemRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.ItemResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.OutstandingInvoicesResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.VendorRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.VendorResponse;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.SalesPurchaseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class SalesPurchaseController {
    private final SalesPurchaseService service;

    public SalesPurchaseController(SalesPurchaseService service) {
        this.service = service;
    }

    @GetMapping("/customers")
    public List<CustomerResponse> customers(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return service.listCustomers(principal.id(), companyId);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody CustomerRequest request) {
        return service.createCustomer(principal.id(), companyId, request);
    }

    @GetMapping("/customers/{customerId}")
    public CustomerResponse customer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID customerId) {
        return service.getCustomer(principal.id(), companyId, customerId);
    }

    @PatchMapping("/customers/{customerId}")
    public CustomerResponse updateCustomer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerRequest request) {
        return service.updateCustomer(principal.id(), companyId, customerId, request);
    }

    @GetMapping("/vendors")
    public List<VendorResponse> vendors(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return service.listVendors(principal.id(), companyId);
    }

    @PostMapping("/vendors")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorResponse createVendor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody VendorRequest request) {
        return service.createVendor(principal.id(), companyId, request);
    }

    @GetMapping("/vendors/{vendorId}")
    public VendorResponse vendor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID vendorId) {
        return service.getVendor(principal.id(), companyId, vendorId);
    }

    @PatchMapping("/vendors/{vendorId}")
    public VendorResponse updateVendor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorRequest request) {
        return service.updateVendor(principal.id(), companyId, vendorId, request);
    }

    @GetMapping("/items")
    public List<ItemResponse> items(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return service.listItems(principal.id(), companyId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse createItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody ItemRequest request) {
        return service.createItem(principal.id(), companyId, request);
    }

    @GetMapping("/items/{itemId}")
    public ItemResponse item(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID itemId) {
        return service.getItem(principal.id(), companyId, itemId);
    }

    @PatchMapping("/items/{itemId}")
    public ItemResponse updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemRequest request) {
        return service.updateItem(principal.id(), companyId, itemId, request);
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return service.listInvoices(principal.id(), companyId);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody InvoiceCreateRequest request) {
        return service.createInvoice(principal.id(), companyId, request);
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse invoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {
        return service.getInvoice(principal.id(), companyId, invoiceId);
    }

    @PatchMapping("/invoices/{invoiceId}")
    public InvoiceResponse updateInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId,
            @Valid @RequestBody InvoiceUpdateRequest request) {
        return service.updateInvoice(principal.id(), companyId, invoiceId, request);
    }

    @PostMapping("/invoices/{invoiceId}/approve")
    public InvoiceResponse approveInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {
        return service.approveInvoice(principal.id(), companyId, invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/post")
    public InvoiceResponse postInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {
        return service.postInvoice(principal.id(), companyId, invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/cancel")
    public InvoiceResponse cancelInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {
        return service.cancelInvoice(principal.id(), companyId, invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoicePaymentResponse addPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId,
            @Valid @RequestBody InvoicePaymentRequest request) {
        return service.addPayment(principal.id(), companyId, invoiceId, request);
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public List<InvoicePaymentResponse> payments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {
        return service.listPayments(principal.id(), companyId, invoiceId);
    }

    @GetMapping("/reports/sales-register")
    public InvoiceRegisterResponse salesRegister(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return service.register(principal.id(), companyId, InvoiceType.SALES);
    }

    @GetMapping("/reports/purchase-register")
    public InvoiceRegisterResponse purchaseRegister(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return service.register(principal.id(), companyId, InvoiceType.PURCHASE);
    }

    @GetMapping("/reports/outstanding-invoices")
    public OutstandingInvoicesResponse outstanding(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return service.outstanding(principal.id(), companyId);
    }
}
