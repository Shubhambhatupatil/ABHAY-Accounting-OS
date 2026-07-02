package com.anvritai.abhay.service;

import com.anvritai.abhay.api.AccountingDtos.LedgerCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.LedgerUpdateRequest;
import com.anvritai.abhay.api.AccountingDtos.OpeningBalanceRequest;
import com.anvritai.abhay.api.AccountingDtos.VoucherCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.VoucherLineRequest;
import com.anvritai.abhay.api.AccountingDtos.VoucherResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.CustomerRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.CustomerResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceCreateRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceLineRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceLineResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoicePaymentRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoicePaymentResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceRegisterResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceRegisterRow;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.InvoiceUpdateRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.ItemRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.ItemResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.OutstandingInvoiceRow;
import com.anvritai.abhay.api.SalesPurchaseDtos.OutstandingInvoicesResponse;
import com.anvritai.abhay.api.SalesPurchaseDtos.VendorRequest;
import com.anvritai.abhay.api.SalesPurchaseDtos.VendorResponse;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.accounting.AiMemoryEvent;
import com.anvritai.abhay.domain.accounting.Ledger;
import com.anvritai.abhay.domain.accounting.LedgerGroup;
import com.anvritai.abhay.domain.accounting.LedgerType;
import com.anvritai.abhay.domain.accounting.NormalBalance;
import com.anvritai.abhay.domain.accounting.Voucher;
import com.anvritai.abhay.domain.gst.GstTreatment;
import com.anvritai.abhay.domain.sales.Customer;
import com.anvritai.abhay.domain.sales.Invoice;
import com.anvritai.abhay.domain.sales.InvoiceItem;
import com.anvritai.abhay.domain.sales.InvoicePayment;
import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.domain.sales.Item;
import com.anvritai.abhay.domain.sales.Vendor;
import com.anvritai.abhay.repository.CompanyRepository;
import com.anvritai.abhay.repository.FinancialYearRepository;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.accounting.AiMemoryEventRepository;
import com.anvritai.abhay.repository.accounting.LedgerGroupRepository;
import com.anvritai.abhay.repository.accounting.LedgerRepository;
import com.anvritai.abhay.repository.accounting.VoucherRepository;
import com.anvritai.abhay.repository.inventory.InventoryUnitRepository;
import com.anvritai.abhay.repository.inventory.ItemCategoryRepository;
import com.anvritai.abhay.repository.sales.CustomerRepository;
import com.anvritai.abhay.repository.sales.InvoicePaymentRepository;
import com.anvritai.abhay.repository.sales.InvoiceRepository;
import com.anvritai.abhay.repository.sales.ItemRepository;
import com.anvritai.abhay.repository.sales.VendorRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesPurchaseService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};

    private final CompanyAccessService access;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final FinancialYearRepository financialYears;
    private final LedgerGroupRepository ledgerGroups;
    private final LedgerRepository ledgers;
    private final CustomerRepository customers;
    private final VendorRepository vendors;
    private final ItemRepository items;
    private final InvoiceRepository invoices;
    private final InvoicePaymentRepository payments;
    private final VoucherRepository vouchers;
    private final AiMemoryEventRepository aiMemoryEvents;
    private final AccountingService accounting;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final GstValidationService gstValidation;
    private final GstReportService gstReports;
    private final InventoryUnitRepository inventoryUnits;
    private final ItemCategoryRepository itemCategories;
    private final InventoryService inventory;
    private final MemoryEventCaptureService memoryEvents;

    public SalesPurchaseService(
            CompanyAccessService access,
            CompanyRepository companies,
            UserRepository users,
            FinancialYearRepository financialYears,
            LedgerGroupRepository ledgerGroups,
            LedgerRepository ledgers,
            CustomerRepository customers,
            VendorRepository vendors,
            ItemRepository items,
            InvoiceRepository invoices,
            InvoicePaymentRepository payments,
            VoucherRepository vouchers,
            AiMemoryEventRepository aiMemoryEvents,
            AccountingService accounting,
            AuditService auditService,
            ObjectMapper objectMapper,
            GstValidationService gstValidation,
            GstReportService gstReports,
            InventoryUnitRepository inventoryUnits,
            ItemCategoryRepository itemCategories,
            InventoryService inventory,
            MemoryEventCaptureService memoryEvents) {
        this.access = access;
        this.companies = companies;
        this.users = users;
        this.financialYears = financialYears;
        this.ledgerGroups = ledgerGroups;
        this.ledgers = ledgers;
        this.customers = customers;
        this.vendors = vendors;
        this.items = items;
        this.invoices = invoices;
        this.payments = payments;
        this.vouchers = vouchers;
        this.aiMemoryEvents = aiMemoryEvents;
        this.accounting = accounting;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.gstValidation = gstValidation;
        this.gstReports = gstReports;
        this.inventoryUnits = inventoryUnits;
        this.itemCategories = itemCategories;
        this.inventory = inventory;
        this.memoryEvents = memoryEvents;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listCustomers(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return customers.findAllByCompanyIdOrderByDisplayName(companyId).stream().map(this::customerResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID userId, UUID companyId, UUID customerId) {
        access.requireMembership(companyId, userId);
        return customerResponse(requireCustomer(companyId, customerId));
    }

    @Transactional
    public CustomerResponse createCustomer(UUID userId, UUID companyId, CustomerRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        String displayName = valueOr(request.displayName(), request.name()).trim();
        BigDecimal opening = money(request.openingBalance());
        UUID groupId = requireGroup(companyId, "Sundry Debtors").getId();
        var ledgerResponse = accounting.createLedger(userId, companyId, new LedgerCreateRequest(
                groupId, displayName + " - Customer", null, NormalBalance.DEBIT, LedgerType.CUSTOMER,
                opening, ZERO));
        Customer customer = new Customer();
        customer.setCompany(requireCompany(companyId));
        customer.setLedger(requireLedger(companyId, ledgerResponse.id()));
        applyCustomer(customer, request);
        customer = customers.save(customer);
        auditService.record(customer.getCompany(), requireUser(userId), "CUSTOMER_CREATED", "CUSTOMER",
                customer.getId(), Map.of("name", customer.getDisplayName(), "ledgerId", customer.getLedger().getId()));
        return customerResponse(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID userId, UUID companyId, UUID customerId, CustomerRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Customer customer = requireCustomer(companyId, customerId);
        String displayName = valueOr(request.displayName(), request.name()).trim();
        accounting.updateLedger(userId, companyId, customer.getLedger().getId(),
                new LedgerUpdateRequest(displayName + " - Customer", null, null, LedgerType.CUSTOMER, request.active()));
        if (request.openingBalance() != null) {
            accounting.setOpeningBalance(userId, companyId, customer.getLedger().getId(),
                    new OpeningBalanceRequest(null, request.openingBalance(), ZERO));
        }
        applyCustomer(customer, request);
        customers.save(customer);
        auditService.record(customer.getCompany(), requireUser(userId), "CUSTOMER_UPDATED", "CUSTOMER",
                customer.getId(), Map.of("name", customer.getDisplayName(), "active", customer.isActive()));
        return customerResponse(customer);
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> listVendors(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return vendors.findAllByCompanyIdOrderByDisplayName(companyId).stream().map(this::vendorResponse).toList();
    }

    @Transactional(readOnly = true)
    public VendorResponse getVendor(UUID userId, UUID companyId, UUID vendorId) {
        access.requireMembership(companyId, userId);
        return vendorResponse(requireVendor(companyId, vendorId));
    }

    @Transactional
    public VendorResponse createVendor(UUID userId, UUID companyId, VendorRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        String displayName = valueOr(request.displayName(), request.name()).trim();
        BigDecimal opening = money(request.openingBalance());
        UUID groupId = requireGroup(companyId, "Sundry Creditors").getId();
        var ledgerResponse = accounting.createLedger(userId, companyId, new LedgerCreateRequest(
                groupId, displayName + " - Vendor", null, NormalBalance.CREDIT, LedgerType.VENDOR,
                ZERO, opening));
        Vendor vendor = new Vendor();
        vendor.setCompany(requireCompany(companyId));
        vendor.setLedger(requireLedger(companyId, ledgerResponse.id()));
        applyVendor(vendor, request);
        vendor = vendors.save(vendor);
        auditService.record(vendor.getCompany(), requireUser(userId), "VENDOR_CREATED", "VENDOR",
                vendor.getId(), Map.of("name", vendor.getDisplayName(), "ledgerId", vendor.getLedger().getId()));
        return vendorResponse(vendor);
    }

    @Transactional
    public VendorResponse updateVendor(UUID userId, UUID companyId, UUID vendorId, VendorRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Vendor vendor = requireVendor(companyId, vendorId);
        String displayName = valueOr(request.displayName(), request.name()).trim();
        accounting.updateLedger(userId, companyId, vendor.getLedger().getId(),
                new LedgerUpdateRequest(displayName + " - Vendor", null, null, LedgerType.VENDOR, request.active()));
        if (request.openingBalance() != null) {
            accounting.setOpeningBalance(userId, companyId, vendor.getLedger().getId(),
                    new OpeningBalanceRequest(null, ZERO, request.openingBalance()));
        }
        applyVendor(vendor, request);
        vendors.save(vendor);
        auditService.record(vendor.getCompany(), requireUser(userId), "VENDOR_UPDATED", "VENDOR",
                vendor.getId(), Map.of("name", vendor.getDisplayName(), "active", vendor.isActive()));
        return vendorResponse(vendor);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> listItems(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return items.findAllByCompanyIdOrderByName(companyId).stream().map(this::itemResponse).toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse getItem(UUID userId, UUID companyId, UUID itemId) {
        access.requireMembership(companyId, userId);
        return itemResponse(requireItem(companyId, itemId));
    }

    @Transactional
    public ItemResponse createItem(UUID userId, UUID companyId, ItemRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        validateItemUniqueness(companyId, null, request.name(), request.sku());
        Item item = new Item();
        item.setCompany(requireCompany(companyId));
        applyItem(item, request);
        item = items.save(item);
        auditService.record(item.getCompany(), requireUser(userId), "ITEM_CREATED", "ITEM", item.getId(),
                Map.of("name", item.getName(), "type", item.getType().name()));
        return itemResponse(item);
    }

    @Transactional
    public ItemResponse updateItem(UUID userId, UUID companyId, UUID itemId, ItemRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Item item = requireItem(companyId, itemId);
        validateItemUniqueness(companyId, item, request.name(), request.sku());
        applyItem(item, request);
        items.save(item);
        auditService.record(item.getCompany(), requireUser(userId), "ITEM_UPDATED", "ITEM", item.getId(),
                Map.of("name", item.getName(), "active", item.isActive()));
        return itemResponse(item);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return invoices.findAllByCompanyIdOrderByInvoiceDateDescInvoiceNumberDesc(companyId).stream()
                .map(this::invoiceResponse).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID userId, UUID companyId, UUID invoiceId) {
        access.requireMembership(companyId, userId);
        return invoiceResponse(requireInvoice(companyId, invoiceId));
    }

    @Transactional
    public InvoiceResponse createInvoice(UUID userId, UUID companyId, InvoiceCreateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Company company = requireCompany(companyId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        requireUnlocked(financialYear);
        validateInvoiceDates(financialYear, request.invoiceDate(), request.dueDate());
        Party party = requireParty(companyId, request.invoiceType(), request.customerId(), request.vendorId());
        if (invoices.existsByCompanyIdAndFinancialYearIdAndInvoiceTypeAndPartyKeyAndInvoiceNumberIgnoreCase(
                companyId, financialYear.getId(), request.invoiceType(), party.id(), request.invoiceNumber().trim())) {
            throw new ConflictException("This invoice number already exists for the party and financial year.");
        }
        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setFinancialYear(financialYear);
        invoice.setInvoiceType(request.invoiceType());
        invoice.setInvoiceNumber(request.invoiceNumber().trim());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setCustomer(party.customer());
        invoice.setVendor(party.vendor());
        invoice.setPartyKey(party.id());
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setGstTreatment(request.gstTreatment() == null ? GstTreatment.NORMAL : request.gstTreatment());
        invoice.setPlaceOfSupply(resolvePlaceOfSupply(company, party, request.placeOfSupply()));
        invoice.setNotes(blankToNull(request.notes()));
        invoice.setCreatedBy(requireUser(userId));
        calculateItems(invoice, company, party, request.items());
        invoice = invoices.save(invoice);
        auditService.record(company, invoice.getCreatedBy(), "INVOICE_DRAFT_CREATED", "INVOICE", invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "invoiceType", invoice.getInvoiceType().name(),
                        "total", invoice.getTotal().toPlainString()));
        return invoiceResponse(invoice);
    }

    @Transactional
    public InvoiceResponse updateInvoice(
            UUID userId, UUID companyId, UUID invoiceId, InvoiceUpdateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Invoice invoice = lockInvoice(companyId, invoiceId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new AccountingRuleException("Only draft invoices can be edited.");
        }
        requireUnlocked(invoice.getFinancialYear());
        validateInvoiceDates(invoice.getFinancialYear(), request.invoiceDate(), request.dueDate());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        if (request.gstTreatment() != null) invoice.setGstTreatment(request.gstTreatment());
        if (request.placeOfSupply() != null) {
            invoice.setPlaceOfSupply(resolvePlaceOfSupply(invoice.getCompany(), party(invoice), request.placeOfSupply()));
        }
        invoice.setNotes(blankToNull(request.notes()));
        invoice.clearItems();
        calculateItems(invoice, invoice.getCompany(), party(invoice), request.items());
        invoices.save(invoice);
        auditService.record(invoice.getCompany(), requireUser(userId), "INVOICE_UPDATED", "INVOICE", invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "total", invoice.getTotal().toPlainString()));
        return invoiceResponse(invoice);
    }

    @Transactional
    public InvoiceResponse approveInvoice(UUID userId, UUID companyId, UUID invoiceId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Invoice invoice = lockInvoice(companyId, invoiceId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new AccountingRuleException("Only draft invoices can be approved.");
        }
        requireUnlocked(invoice.getFinancialYear());
        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setApprovedBy(requireUser(userId));
        invoice.setApprovedAt(Instant.now());
        invoices.save(invoice);
        auditService.record(invoice.getCompany(), invoice.getApprovedBy(), "INVOICE_APPROVED", "INVOICE",
                invoice.getId(), Map.of("invoiceNumber", invoice.getInvoiceNumber()));
        return invoiceResponse(invoice);
    }

    @Transactional
    public InvoiceResponse postInvoice(UUID userId, UUID companyId, UUID invoiceId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Invoice invoice = lockInvoice(companyId, invoiceId);
        if (invoice.getStatus() == InvoiceStatus.POSTED || invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("This invoice has already been posted.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new AccountingRuleException("A cancelled invoice cannot be posted.");
        }
        if (invoice.getStatus() != InvoiceStatus.APPROVED) {
            throw new AccountingRuleException("Approve the invoice before posting it to books.");
        }
        requireUnlocked(invoice.getFinancialYear());
        inventory.postInvoice(userId, invoice);
        List<VoucherLineRequest> lines = invoiceVoucherLines(companyId, invoice);
        VoucherResponse draft = accounting.createVoucher(userId, companyId, new VoucherCreateRequest(
                invoice.getInvoiceType().name(), invoice.getInvoiceDate(),
                invoice.getInvoiceType().name() + " invoice " + invoice.getInvoiceNumber(), lines));
        accounting.postVoucher(userId, companyId, draft.id());
        Voucher voucher = vouchers.findByIdAndCompanyId(draft.id(), companyId)
                .orElseThrow(() -> new IllegalStateException("Posted invoice voucher was not found."));
        invoice.setPostedVoucher(voucher);
        invoice.setStatus(InvoiceStatus.POSTED);
        invoice.setPostedAt(Instant.now());
        invoices.save(invoice);
        gstValidation.assessOnPosting(invoice);
        gstReports.refreshLiability(invoice);
        User actor = requireUser(userId);
        auditService.record(invoice.getCompany(), actor, "INVOICE_POSTED", "INVOICE", invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "voucherId", voucher.getId(),
                        "total", invoice.getTotal().toPlainString()));
        createMemoryEvent(invoice, "INVOICE_POSTED");
        return invoiceResponse(invoice);
    }

    @Transactional
    public InvoiceResponse cancelInvoice(UUID userId, UUID companyId, UUID invoiceId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Invoice invoice = lockInvoice(companyId, invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new AccountingRuleException("Paid invoices cannot be cancelled until payments are reversed.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return invoiceResponse(invoice);
        }
        if (invoice.getStatus() == InvoiceStatus.POSTED) {
            if (invoice.getPostedVoucher() == null) {
                throw new AccountingRuleException("Posted invoice voucher is missing and cannot be reversed safely.");
            }
            accounting.reverseVoucher(userId, companyId, invoice.getPostedVoucher().getId());
            inventory.reverseInvoice(userId, invoice);
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(Instant.now());
        invoices.save(invoice);
        gstReports.refreshLiability(invoice);
        auditService.record(invoice.getCompany(), requireUser(userId), "INVOICE_CANCELLED", "INVOICE",
                invoice.getId(), Map.of("invoiceNumber", invoice.getInvoiceNumber()));
        return invoiceResponse(invoice);
    }

    @Transactional
    public InvoicePaymentResponse addPayment(
            UUID userId, UUID companyId, UUID invoiceId, InvoicePaymentRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Invoice invoice = lockInvoice(companyId, invoiceId);
        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new AccountingRuleException("Payments can be recorded only against posted, unpaid invoices.");
        }
        BigDecimal amount = money(request.amount());
        BigDecimal currentPaid = money(payments.totalPaid(companyId, invoiceId));
        BigDecimal outstanding = money(invoice.getTotal().subtract(currentPaid));
        if (amount.compareTo(outstanding) > 0) {
            throw new AccountingRuleException("Payment amount cannot exceed the invoice outstanding balance.");
        }
        Ledger settlementLedger = settlementLedger(companyId, request.mode());
        Ledger partyLedger = party(invoice).ledger();
        List<VoucherLineRequest> lines = invoice.getInvoiceType() == InvoiceType.SALES
                ? List.of(debit(settlementLedger, amount), credit(partyLedger, amount))
                : List.of(debit(partyLedger, amount), credit(settlementLedger, amount));
        String voucherType = invoice.getInvoiceType() == InvoiceType.SALES ? "RECEIPT" : "PAYMENT";
        VoucherResponse draft = accounting.createVoucher(userId, companyId, new VoucherCreateRequest(
                voucherType, request.paymentDate(),
                "Payment for invoice " + invoice.getInvoiceNumber() + referenceSuffix(request.reference()), lines));
        accounting.postVoucher(userId, companyId, draft.id());
        Voucher voucher = vouchers.findByIdAndCompanyId(draft.id(), companyId)
                .orElseThrow(() -> new IllegalStateException("Payment voucher was not found."));
        InvoicePayment payment = new InvoicePayment();
        payment.setCompany(invoice.getCompany());
        payment.setInvoice(invoice);
        payment.setPaymentDate(request.paymentDate());
        payment.setAmount(amount);
        payment.setMode(request.mode().trim().toUpperCase(Locale.ROOT));
        payment.setReference(blankToNull(request.reference()));
        payment.setLinkedVoucher(voucher);
        payment.setCreatedBy(requireUser(userId));
        payment = payments.save(payment);
        if (money(currentPaid.add(amount)).compareTo(invoice.getTotal()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoices.save(invoice);
        }
        auditService.record(invoice.getCompany(), payment.getCreatedBy(), "INVOICE_PAYMENT_RECORDED",
                "INVOICE_PAYMENT", payment.getId(), Map.of("invoiceId", invoice.getId(),
                        "amount", amount.toPlainString(), "voucherId", voucher.getId()));
        Map<String, Object> paymentMemory = new java.util.LinkedHashMap<>();
        paymentMemory.put("invoiceId", invoice.getId());
        paymentMemory.put("party", party(invoice).name());
        paymentMemory.put("partyLedgerId", partyLedger.getId());
        paymentMemory.put("amount", amount);
        paymentMemory.put("voucherType", voucherType);
        paymentMemory.put("mode", payment.getMode());
        if (payment.getReference() != null) paymentMemory.put("reference", payment.getReference());
        memoryEvents.record(invoice.getCompany(), userId, "INVOICE_PAYMENT_RECORDED", "INVOICE_PAYMENT",
                payment.getId(), paymentMemory);
        return paymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<InvoicePaymentResponse> listPayments(UUID userId, UUID companyId, UUID invoiceId) {
        access.requireMembership(companyId, userId);
        requireInvoice(companyId, invoiceId);
        return payments.findAllByCompanyIdAndInvoiceIdOrderByPaymentDateAscCreatedAtAsc(companyId, invoiceId)
                .stream().map(this::paymentResponse).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceRegisterResponse register(UUID userId, UUID companyId, InvoiceType type) {
        access.requireMembership(companyId, userId);
        List<InvoiceRegisterRow> rows = invoices
                .findAllByCompanyIdAndInvoiceTypeOrderByInvoiceDateDescInvoiceNumberDesc(companyId, type)
                .stream().map(this::registerRow).toList();
        return new InvoiceRegisterResponse(type, rows,
                sum(rows.stream().map(InvoiceRegisterRow::taxableAmount).toList()),
                sum(rows.stream().map(InvoiceRegisterRow::gstAmount).toList()),
                sum(rows.stream().map(InvoiceRegisterRow::total).toList()));
    }

    @Transactional(readOnly = true)
    public OutstandingInvoicesResponse outstanding(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        List<OutstandingInvoiceRow> rows = invoices
                .findAllByCompanyIdAndStatusInOrderByDueDate(companyId, List.of(InvoiceStatus.POSTED))
                .stream().map(this::outstandingRow).filter(row -> row.balance().signum() > 0).toList();
        return new OutstandingInvoicesResponse(rows, sum(rows.stream().map(OutstandingInvoiceRow::balance).toList()));
    }

    private void applyCustomer(Customer customer, CustomerRequest request) {
        customer.setName(request.name().trim());
        customer.setDisplayName(valueOr(request.displayName(), request.name()).trim());
        customer.setGstin(upperOrNull(request.gstin()));
        customer.setPan(upperOrNull(request.pan()));
        customer.setEmail(lowerOrNull(request.email()));
        customer.setPhone(blankToNull(request.phone()));
        customer.setBillingAddress(blankToNull(request.billingAddress()));
        customer.setShippingAddress(blankToNull(request.shippingAddress()));
        customer.setState(blankToNull(request.state()));
        customer.setCountry(valueOr(request.country(), "India").trim());
        customer.setCreditLimit(money(request.creditLimit()));
        customer.setPaymentTermsDays(request.paymentTermsDays() == null ? 0 : request.paymentTermsDays());
        customer.setOpeningBalance(money(request.openingBalance()));
        customer.setActive(request.active() == null || request.active());
    }

    private void applyVendor(Vendor vendor, VendorRequest request) {
        vendor.setName(request.name().trim());
        vendor.setDisplayName(valueOr(request.displayName(), request.name()).trim());
        vendor.setGstin(upperOrNull(request.gstin()));
        vendor.setPan(upperOrNull(request.pan()));
        vendor.setEmail(lowerOrNull(request.email()));
        vendor.setPhone(blankToNull(request.phone()));
        vendor.setAddress(blankToNull(request.address()));
        vendor.setState(blankToNull(request.state()));
        vendor.setCountry(valueOr(request.country(), "India").trim());
        vendor.setPaymentTermsDays(request.paymentTermsDays() == null ? 0 : request.paymentTermsDays());
        vendor.setOpeningBalance(money(request.openingBalance()));
        vendor.setActive(request.active() == null || request.active());
    }

    private void applyItem(Item item, ItemRequest request) {
        item.setName(request.name().trim());
        item.setType(request.type());
        item.setSku(upperOrNull(request.sku()));
        item.setHsnSac(upperOrNull(request.hsnSac()));
        item.setUnit(request.unit().trim());
        item.setSalesPrice(money(request.salesPrice()));
        item.setPurchasePrice(money(request.purchasePrice()));
        item.setGstRate(rate(request.gstRate()));
        item.setInventoryUnit(request.inventoryUnitId() == null ? null : inventoryUnits
                .findByIdAndCompanyId(request.inventoryUnitId(), item.getCompany().getId())
                .orElseThrow(() -> new NotFoundException("Inventory unit not found.")));
        item.setItemCategory(request.itemCategoryId() == null ? null : itemCategories
                .findByIdAndCompanyId(request.itemCategoryId(), item.getCompany().getId())
                .orElseThrow(() -> new NotFoundException("Item category not found.")));
        item.setReorderLevel(request.reorderLevel() == null
                ? BigDecimal.ZERO.setScale(4) : request.reorderLevel().setScale(4, RoundingMode.HALF_UP));
        item.setTrackInventory(request.type() == com.anvritai.abhay.domain.sales.ItemType.PRODUCT);
        item.setActive(request.active() == null || request.active());
    }

    private void calculateItems(
            Invoice invoice, Company company, Party party, List<InvoiceLineRequest> requests) {
        boolean intraState = isIntraState(company, invoice.getPlaceOfSupply());
        BigDecimal subtotal = ZERO;
        BigDecimal cgst = ZERO;
        BigDecimal sgst = ZERO;
        BigDecimal igst = ZERO;
        BigDecimal cess = ZERO;
        int lineNumber = 1;
        for (InvoiceLineRequest request : requests) {
            Item item = request.itemId() == null ? null : requireItem(company.getId(), request.itemId());
            if (item != null && !item.isActive()) {
                throw new AccountingRuleException("Inactive items cannot be used on invoices.");
            }
            BigDecimal quantity = request.quantity().setScale(4, RoundingMode.HALF_UP);
            BigDecimal unitPrice = money(request.unitPrice());
            BigDecimal gstRate = invoice.getGstTreatment() == GstTreatment.COMPOSITION
                    ? ZERO : rate(request.gstRate());
            BigDecimal cessRate = invoice.getGstTreatment() == GstTreatment.COMPOSITION
                    ? ZERO : rate(request.cessRate());
            BigDecimal taxable = money(quantity.multiply(unitPrice));
            BigDecimal tax = money(taxable.multiply(gstRate).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            BigDecimal lineCess = money(taxable.multiply(cessRate).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            BigDecimal lineCgst = ZERO;
            BigDecimal lineSgst = ZERO;
            BigDecimal lineIgst = ZERO;
            if (intraState) {
                lineCgst = money(tax.divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP));
                lineSgst = money(tax.subtract(lineCgst));
            } else {
                lineIgst = tax;
            }
            InvoiceItem line = new InvoiceItem();
            line.setItem(item);
            line.setLineNumber(lineNumber++);
            line.setDescription(request.description().trim());
            line.setQuantity(quantity);
            line.setUnitPrice(unitPrice);
            line.setGstRate(gstRate);
            line.setTaxableAmount(taxable);
            line.setCgstAmount(lineCgst);
            line.setSgstAmount(lineSgst);
            line.setIgstAmount(lineIgst);
            line.setCessRate(cessRate);
            line.setCessAmount(lineCess);
            BigDecimal chargedTax = invoice.getGstTreatment() == GstTreatment.REVERSE_CHARGE ? ZERO : tax;
            BigDecimal chargedCess = invoice.getGstTreatment() == GstTreatment.REVERSE_CHARGE ? ZERO : lineCess;
            line.setLineTotal(money(taxable.add(chargedTax).add(chargedCess)));
            invoice.addItem(line);
            subtotal = subtotal.add(taxable);
            cgst = cgst.add(lineCgst);
            sgst = sgst.add(lineSgst);
            igst = igst.add(lineIgst);
            cess = cess.add(lineCess);
        }
        invoice.setSubtotal(money(subtotal));
        invoice.setCgstTotal(money(cgst));
        invoice.setSgstTotal(money(sgst));
        invoice.setIgstTotal(money(igst));
        invoice.setCessTotal(money(cess));
        BigDecimal chargedTax = invoice.getGstTreatment() == GstTreatment.REVERSE_CHARGE
                ? ZERO : cgst.add(sgst).add(igst).add(cess);
        invoice.setTotal(money(subtotal.add(chargedTax)));
    }

    private List<VoucherLineRequest> invoiceVoucherLines(UUID companyId, Invoice invoice) {
        List<VoucherLineRequest> lines = new ArrayList<>();
        Ledger partyLedger = party(invoice).ledger();
        if (invoice.getInvoiceType() == InvoiceType.SALES) {
            lines.add(debit(partyLedger, invoice.getTotal()));
            lines.add(credit(requireLedgerByName(companyId, "Sales", null), invoice.getSubtotal()));
            if (invoice.getGstTreatment() != GstTreatment.REVERSE_CHARGE) {
                addTaxLine(lines, requireLedgerByName(companyId, "CGST Output", "GST Output"), invoice.getCgstTotal(), false);
                addTaxLine(lines, requireLedgerByName(companyId, "SGST Output", "GST Output"), invoice.getSgstTotal(), false);
                addTaxLine(lines, requireLedgerByName(companyId, "IGST Output", "GST Output"), invoice.getIgstTotal(), false);
                addTaxLine(lines, requireLedgerByName(companyId, "CESS Output", "GST Output"), invoice.getCessTotal(), false);
            }
        } else {
            lines.add(debit(requireLedgerByName(companyId, "Purchase", null), invoice.getSubtotal()));
            addTaxLine(lines, requireLedgerByName(companyId, "CGST Input", "GST Input"), invoice.getCgstTotal(), true);
            addTaxLine(lines, requireLedgerByName(companyId, "SGST Input", "GST Input"), invoice.getSgstTotal(), true);
            addTaxLine(lines, requireLedgerByName(companyId, "IGST Input", "GST Input"), invoice.getIgstTotal(), true);
            addTaxLine(lines, requireLedgerByName(companyId, "CESS Input", "GST Input"), invoice.getCessTotal(), true);
            lines.add(credit(partyLedger, invoice.getTotal()));
            if (invoice.getGstTreatment() == GstTreatment.REVERSE_CHARGE) {
                addTaxLine(lines, requireLedgerByName(companyId, "CGST Output", "GST Output"), invoice.getCgstTotal(), false);
                addTaxLine(lines, requireLedgerByName(companyId, "SGST Output", "GST Output"), invoice.getSgstTotal(), false);
                addTaxLine(lines, requireLedgerByName(companyId, "IGST Output", "GST Output"), invoice.getIgstTotal(), false);
                addTaxLine(lines, requireLedgerByName(companyId, "CESS Output", "GST Output"), invoice.getCessTotal(), false);
            }
        }
        return lines;
    }

    private void addTaxLine(List<VoucherLineRequest> lines, Ledger ledger, BigDecimal amount, boolean debit) {
        if (amount.signum() > 0) {
            lines.add(debit ? debit(ledger, amount) : credit(ledger, amount));
        }
    }

    private VoucherLineRequest debit(Ledger ledger, BigDecimal amount) {
        return new VoucherLineRequest(ledger.getId(), money(amount), ZERO, null);
    }

    private VoucherLineRequest credit(Ledger ledger, BigDecimal amount) {
        return new VoucherLineRequest(ledger.getId(), ZERO, money(amount), null);
    }

    private Ledger settlementLedger(UUID companyId, String mode) {
        LedgerType type = "CASH".equalsIgnoreCase(mode.trim()) ? LedgerType.CASH : LedgerType.BANK;
        return ledgers.findAllByCompanyIdAndLedgerTypeInAndActiveTrueOrderByName(companyId, List.of(type)).stream()
                .findFirst().orElseThrow(() -> new AccountingRuleException(
                        type == LedgerType.CASH ? "No active cash ledger exists." : "No active bank ledger exists."));
    }

    private Party requireParty(UUID companyId, InvoiceType type, UUID customerId, UUID vendorId) {
        if (type == InvoiceType.SALES && customerId != null && vendorId == null) {
            Customer customer = requireCustomer(companyId, customerId);
            if (!customer.isActive()) throw new AccountingRuleException("Inactive customers cannot be invoiced.");
            return new Party(customer.getId(), customer.getDisplayName(), customer.getState(), customer.getGstin(),
                    customer.getLedger(), customer, null);
        }
        if (type == InvoiceType.PURCHASE && vendorId != null && customerId == null) {
            Vendor vendor = requireVendor(companyId, vendorId);
            if (!vendor.isActive()) throw new AccountingRuleException("Inactive vendors cannot be invoiced.");
            return new Party(vendor.getId(), vendor.getDisplayName(), vendor.getState(), vendor.getGstin(),
                    vendor.getLedger(), null, vendor);
        }
        throw new IllegalArgumentException("Sales invoices require one customer; purchase invoices require one vendor.");
    }

    private Party party(Invoice invoice) {
        return invoice.getInvoiceType() == InvoiceType.SALES
                ? new Party(invoice.getCustomer().getId(), invoice.getCustomer().getDisplayName(),
                        invoice.getCustomer().getState(), invoice.getCustomer().getGstin(),
                        invoice.getCustomer().getLedger(), invoice.getCustomer(), null)
                : new Party(invoice.getVendor().getId(), invoice.getVendor().getDisplayName(),
                        invoice.getVendor().getState(), invoice.getVendor().getGstin(),
                        invoice.getVendor().getLedger(), null, invoice.getVendor());
    }

    private boolean isIntraState(Company company, String placeOfSupply) {
        String companyState = blankToNull(company.getStateCode());
        return companyState == null || placeOfSupply == null || companyState.equalsIgnoreCase(placeOfSupply);
    }

    private String resolvePlaceOfSupply(Company company, Party party, String requested) {
        String place = blankToNull(requested);
        if (place == null && party.gstin() != null && party.gstin().length() >= 2) place = party.gstin().substring(0, 2);
        if (place == null && party.state() != null && party.state().matches("[0-9]{2}")) place = party.state();
        if (place == null) place = company.getStateCode();
        return place;
    }

    private void createMemoryEvent(Invoice invoice, String eventType) {
        Party invoiceParty = party(invoice);
        List<Map<String, Object>> itemMemory = new ArrayList<>();
        for (InvoiceItem line : invoice.getItems()) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            if (line.getItem() != null) {
                item.put("itemId", line.getItem().getId());
                item.put("itemName", line.getItem().getName());
                if (line.getItem().getHsnSac() != null) item.put("hsnSac", line.getItem().getHsnSac());
            }
            item.put("description", line.getDescription());
            item.put("gstRate", line.getGstRate());
            itemMemory.add(item);
        }
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("invoiceNumber", invoice.getInvoiceNumber());
        details.put("invoiceType", invoice.getInvoiceType().name());
        details.put("party", invoiceParty.name());
        details.put("partyId", invoiceParty.id());
        details.put("partyLedgerId", invoiceParty.ledger().getId());
        details.put("total", invoice.getTotal().toPlainString());
        details.put("gstTreatment", invoice.getGstTreatment().name());
        details.put("gstAmount", invoice.getCgstTotal().add(invoice.getSgstTotal()).add(invoice.getIgstTotal()));
        details.put("cessAmount", invoice.getCessTotal());
        details.put("items", itemMemory);
        AiMemoryEvent event = new AiMemoryEvent();
        event.setCompany(invoice.getCompany());
        event.setEventType(eventType);
        event.setEntityType("INVOICE");
        event.setEntityId(invoice.getId());
        event.setProcessingStatus("PENDING");
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI memory event could not be serialized.", exception);
        }
        aiMemoryEvents.save(event);
        memoryEvents.record(invoice.getCompany(), eventType, "INVOICE", invoice.getId(), details);
    }

    private InvoiceRegisterRow registerRow(Invoice invoice) {
        Party party = party(invoice);
        return new InvoiceRegisterRow(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
                invoice.getDueDate(), party.name(), party.gstin(), invoice.getSubtotal(),
                money(invoice.getCgstTotal().add(invoice.getSgstTotal()).add(invoice.getIgstTotal())),
                invoice.getTotal(), invoice.getStatus());
    }

    private OutstandingInvoiceRow outstandingRow(Invoice invoice) {
        BigDecimal paid = money(payments.totalPaid(invoice.getCompany().getId(), invoice.getId()));
        BigDecimal balance = money(invoice.getTotal().subtract(paid).max(BigDecimal.ZERO));
        long overdue = invoice.getDueDate().isBefore(LocalDate.now())
                ? ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now()) : 0;
        return new OutstandingInvoiceRow(invoice.getId(), invoice.getInvoiceType(), party(invoice).name(),
                invoice.getInvoiceNumber(), invoice.getInvoiceDate(), invoice.getDueDate(), invoice.getTotal(),
                paid, balance, overdue);
    }

    private CustomerResponse customerResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getLedger().getId(), customer.getName(),
                customer.getDisplayName(), customer.getGstin(), customer.getPan(), customer.getEmail(),
                customer.getPhone(), customer.getBillingAddress(), customer.getShippingAddress(),
                customer.getState(), customer.getCountry(), money(customer.getCreditLimit()),
                customer.getPaymentTermsDays(), money(customer.getOpeningBalance()), customer.isActive(),
                customer.getCreatedAt(), customer.getUpdatedAt());
    }

    private VendorResponse vendorResponse(Vendor vendor) {
        return new VendorResponse(vendor.getId(), vendor.getLedger().getId(), vendor.getName(),
                vendor.getDisplayName(), vendor.getGstin(), vendor.getPan(), vendor.getEmail(), vendor.getPhone(),
                vendor.getAddress(), vendor.getState(), vendor.getCountry(), vendor.getPaymentTermsDays(),
                money(vendor.getOpeningBalance()), vendor.isActive(), vendor.getCreatedAt(), vendor.getUpdatedAt());
    }

    private ItemResponse itemResponse(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getType(), item.getSku(), item.getHsnSac(),
                item.getUnit(), money(item.getSalesPrice()), money(item.getPurchasePrice()), rate(item.getGstRate()),
                item.getInventoryUnit() == null ? null : item.getInventoryUnit().getId(),
                item.getItemCategory() == null ? null : item.getItemCategory().getId(),
                item.getReorderLevel(), item.isTrackInventory(),
                item.isActive(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private InvoiceResponse invoiceResponse(Invoice invoice) {
        BigDecimal paid = money(payments.totalPaid(invoice.getCompany().getId(), invoice.getId()));
        List<InvoiceLineResponse> itemResponses = invoice.getItems().stream().map(line -> new InvoiceLineResponse(
                line.getId(), line.getItem() == null ? null : line.getItem().getId(), line.getLineNumber(),
                line.getDescription(), line.getQuantity(), money(line.getUnitPrice()), rate(line.getGstRate()),
                money(line.getTaxableAmount()), money(line.getCgstAmount()), money(line.getSgstAmount()),
                money(line.getIgstAmount()), rate(line.getCessRate()), money(line.getCessAmount()),
                money(line.getLineTotal()))).toList();
        Party party = party(invoice);
        return new InvoiceResponse(invoice.getId(), invoice.getFinancialYear().getId(), invoice.getInvoiceType(),
                invoice.getInvoiceNumber(), invoice.getInvoiceDate(), invoice.getDueDate(),
                invoice.getCustomer() == null ? null : invoice.getCustomer().getId(),
                invoice.getVendor() == null ? null : invoice.getVendor().getId(), party.name(), invoice.getStatus(),
                money(invoice.getSubtotal()), money(invoice.getCgstTotal()), money(invoice.getSgstTotal()),
                money(invoice.getIgstTotal()), money(invoice.getCessTotal()), invoice.getGstTreatment(),
                invoice.getPlaceOfSupply(), money(invoice.getTotal()), paid,
                money(invoice.getTotal().subtract(paid).max(BigDecimal.ZERO)), invoice.getNotes(),
                invoice.getPostedVoucher() == null ? null : invoice.getPostedVoucher().getId(), itemResponses,
                invoice.getCreatedAt(), invoice.getUpdatedAt());
    }

    private InvoicePaymentResponse paymentResponse(InvoicePayment payment) {
        return new InvoicePaymentResponse(payment.getId(), payment.getPaymentDate(), money(payment.getAmount()),
                payment.getMode(), payment.getReference(), payment.getLinkedVoucher().getId(), payment.getCreatedAt());
    }

    private void validateItemUniqueness(UUID companyId, Item current, String name, String sku) {
        if ((current == null || !current.getName().equalsIgnoreCase(name.trim()))
                && items.existsByCompanyIdAndNameIgnoreCase(companyId, name.trim())) {
            throw new ConflictException("An item with this name already exists.");
        }
        String normalizedSku = upperOrNull(sku);
        if (normalizedSku != null
                && (current == null || current.getSku() == null || !current.getSku().equalsIgnoreCase(normalizedSku))
                && items.existsByCompanyIdAndSkuIgnoreCase(companyId, normalizedSku)) {
            throw new ConflictException("An item with this SKU already exists.");
        }
    }

    private void validateInvoiceDates(FinancialYear year, LocalDate invoiceDate, LocalDate dueDate) {
        if (dueDate.isBefore(invoiceDate)) throw new IllegalArgumentException("Due date cannot precede invoice date.");
        if (invoiceDate.isBefore(year.getStartsOn()) || invoiceDate.isAfter(year.getEndsOn())) {
            throw new AccountingRuleException("Invoice date must fall inside the active financial year.");
        }
    }

    private void requireUnlocked(FinancialYear year) {
        if (year.isLocked()) throw new AccountingRuleException("This financial year is locked.");
    }

    private LedgerGroup requireGroup(UUID companyId, String name) {
        return ledgerGroups.findAllByCompanyIdOrderByName(companyId).stream()
                .filter(group -> group.getName().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new AccountingRuleException("Required ledger group is missing: " + name));
    }

    private Ledger requireLedgerByName(UUID companyId, String name, String fallback) {
        return ledgers.findAllByCompanyIdOrderByName(companyId).stream()
                .filter(ledger -> ledger.getName().equalsIgnoreCase(name))
                .findFirst()
                .or(() -> fallback == null ? java.util.Optional.empty()
                        : ledgers.findAllByCompanyIdOrderByName(companyId).stream()
                                .filter(ledger -> ledger.getName().equalsIgnoreCase(fallback)).findFirst())
                .orElseThrow(() -> new AccountingRuleException("Required ledger is missing: " + name));
    }

    private Company requireCompany(UUID companyId) {
        return companies.findById(companyId).orElseThrow(() -> new NotFoundException("Company not found."));
    }
    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
    }
    private Customer requireCustomer(UUID companyId, UUID customerId) {
        return customers.findByIdAndCompanyId(customerId, companyId)
                .orElseThrow(() -> new NotFoundException("Customer not found."));
    }
    private Vendor requireVendor(UUID companyId, UUID vendorId) {
        return vendors.findByIdAndCompanyId(vendorId, companyId)
                .orElseThrow(() -> new NotFoundException("Vendor not found."));
    }
    private Item requireItem(UUID companyId, UUID itemId) {
        return items.findByIdAndCompanyId(itemId, companyId)
                .orElseThrow(() -> new NotFoundException("Item not found."));
    }
    private Ledger requireLedger(UUID companyId, UUID ledgerId) {
        return ledgers.findByIdAndCompanyId(ledgerId, companyId)
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
    }
    private Invoice requireInvoice(UUID companyId, UUID invoiceId) {
        return invoices.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new NotFoundException("Invoice not found."));
    }
    private Invoice lockInvoice(UUID companyId, UUID invoiceId) {
        return invoices.lockByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new NotFoundException("Invoice not found."));
    }
    private FinancialYear activeFinancialYear(UUID companyId) {
        return financialYears.findFirstByCompanyIdAndActiveTrueOrderByStartsOnDesc(companyId)
                .orElseThrow(() -> new AccountingRuleException("No active financial year exists for this company."));
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return money(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
    private BigDecimal rate(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
    private String lowerOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
    private String valueOr(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String referenceSuffix(String reference) {
        return reference == null || reference.isBlank() ? "" : " (" + reference.trim() + ")";
    }

    private record Party(
            UUID id, String name, String state, String gstin, Ledger ledger, Customer customer, Vendor vendor) {
    }
}
