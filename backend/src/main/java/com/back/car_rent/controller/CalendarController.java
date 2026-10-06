package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.dto.CalendarEventDTO;
import com.back.car_rent.model.Contract;
import com.back.car_rent.repository.ContractRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final ContractRepository contractRepository;

    public CalendarController(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @PreAuthorize("@perm.canRead('contracts')")
    @GetMapping("/events")
    public List<CalendarEventDTO> getEvents() {
        List<Contract> contracts = contractRepository.findAll();
        return contracts.stream().map(contract -> {
            String color = switch (contract.getStatus() != null ? contract.getStatus() : "") {
                case "Active" -> "bg-success-transparent";
                case "Reserved" -> "bg-warning-transparent";
                case "Completed" -> "bg-secondary-transparent";
                case "Canceled" -> "bg-danger-transparent";
                default -> "bg-primary-transparent";
            };

            Map<String, Object> props = new HashMap<>();
            props.put("type", "booking");
            props.put("car", contract.getLicensePlate());
            props.put("client", contract.getClientName());
            props.put("contractId", contract.getContractId());
            props.put("paymentStatus", contract.getPaymentStatus());

            return CalendarEventDTO.builder()
                    .id(contract.getContractId())
                    .title(contract.getClientName() + " - " + contract.getCarMake() + " " + contract.getCarModel())
                    .start(contract.getStartDate())
                    .end(contract.getEndDate())
                    .className(color)
                    .extendedProps(props)
                    .build();
        }).collect(Collectors.toList());
    }
}