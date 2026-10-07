package com.back.car_rent.payments;

import com.back.car_rent.config.ApiException;
import com.back.car_rent.model.Contract;
import com.back.car_rent.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Records payments and keeps each contract's payment status in step with them.
 *
 * <p>Recording a payment locks the contract row first (like a booking locks the car), so two people entering a
 * payment for the same contract at once cannot together pay more than it costs.
 */
@Service
public class PaymentService {

    /** Rounding slack: 0.004 left on a 1,234.56 contract still counts as paid. */
    private static final double CENT = 0.005;

    private final PaymentRepository payments;
    private final ContractRepository contracts;

    public PaymentService(PaymentRepository payments, ContractRepository contracts) {
        this.payments = payments;
        this.contracts = contracts;
    }

    /** Pending when nothing was paid, Partial while something is left, Paid once the total is covered. */
    public static String statusFor(double paid, Double total) {
        double due = total == null ? 0 : total;
        if (paid <= CENT) {
            return "Pending";
        }
        return paid + CENT >= due ? "Paid" : "Partial";
    }

    /** The status a contract should show, from what has been paid on it so far (a new contract: Pending). */
    public String statusOf(Contract contract) {
        double paid = contract.getId() == null ? 0 : payments.totalPaid(contract.getId());
        return statusFor(paid, contract.getTotalValue());
    }

    @Transactional(readOnly = true)
    public List<Payment> findAll() {
        return payments.findAll();
    }

    @Transactional(readOnly = true)
    public List<Payment> forContract(Long contractRef) {
        return payments.findByContractRefOrderByDateAscIdAsc(contractRef);
    }

    @Transactional
    public Payment record(Payment payment) {
        return save(payment, null);
    }

    @Transactional
    public Payment update(Long id, Payment payment) {
        Payment existing = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found"));
        if (!existing.getContractRef().equals(payment.getContractRef())) {
            throw ApiException.badRequest("A payment cannot be moved to another contract; delete it and record a new one");
        }
        payment.setId(id);
        payment.setPaymentId(existing.getPaymentId());
        return save(payment, existing.getAmount());
    }

    @Transactional
    public void delete(Long id) {
        Payment existing = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found"));
        Contract contract = lock(existing.getContractRef());
        payments.delete(existing);
        payments.flush();
        refreshStatus(contract);
    }

    private Payment save(Payment payment, Double previousAmount) {
        if (payment.getContractRef() == null) {
            throw ApiException.badRequest("Choose a contract");
        }
        Contract contract = lock(payment.getContractRef());
        if ("Canceled".equals(contract.getStatus())) {
            throw ApiException.badRequest("Contract " + contract.getContractId() + " is canceled; nothing is due on it");
        }
        double amount = payment.getAmount() == null ? 0 : payment.getAmount();
        if (amount <= 0) {
            throw ApiException.badRequest("The amount must be more than zero");
        }
        date(payment.getDate());
        double total = contract.getTotalValue() == null ? 0 : contract.getTotalValue();
        double alreadyPaid = payments.totalPaid(contract.getId()) - (previousAmount == null ? 0 : previousAmount);
        double left = total - alreadyPaid;
        if (amount > left + CENT) {
            throw ApiException.badRequest(String.format(
                    "Contract %s has %.2f left to pay; %.2f is too much", contract.getContractId(), Math.max(0, left), amount));
        }
        payment.setContractId(contract.getContractId());
        payment.setClientName(contract.getClientName());
        Payment saved = payments.saveAndFlush(payment);
        if (saved.getPaymentId() == null || saved.getPaymentId().isBlank()) {
            saved.setPaymentId("PY-" + (1000 + saved.getId()));
        }
        refreshStatus(contract);
        return saved;
    }

    private Contract lock(Long contractRef) {
        return contracts.lockById(contractRef)
                .orElseThrow(() -> ApiException.badRequest("Unknown contract " + contractRef));
    }

    private void refreshStatus(Contract contract) {
        contract.setPaymentStatus(statusFor(payments.totalPaid(contract.getId()), contract.getTotalValue()));
        contracts.save(contract);
    }

    private static void date(String value) {
        try {
            LocalDate.parse(value == null ? "" : value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Date must look like 2026-03-31");
        }
    }
}
