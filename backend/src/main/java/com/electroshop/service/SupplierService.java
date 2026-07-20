package com.electroshop.service;

import com.electroshop.dto.SupplierDto;
import com.electroshop.dto.SupplierRequest;
import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Supplier;
import com.electroshop.repository.PurchaseRepository;
import com.electroshop.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;

    public SupplierService(SupplierRepository supplierRepository, PurchaseRepository purchaseRepository) {
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
    }

    @Transactional(readOnly = true)
    public Page<SupplierDto> list(String search, Pageable pageable) {
        Page<Supplier> page = (search == null || search.isBlank())
                ? supplierRepository.findAll(pageable)
                : supplierRepository.findByNameContainingIgnoreCaseOrContactNameContainingIgnoreCase(
                        search, search, pageable);
        return page.map(SupplierDto::from);
    }

    @Transactional(readOnly = true)
    public SupplierDto getById(Long id) {
        return SupplierDto.from(findEntity(id));
    }

    public SupplierDto create(SupplierRequest req) {
        Supplier s = new Supplier();
        apply(s, req);
        return SupplierDto.from(supplierRepository.save(s));
    }

    public SupplierDto update(Long id, SupplierRequest req) {
        Supplier s = findEntity(id);
        apply(s, req);
        return SupplierDto.from(supplierRepository.save(s));
    }

    public void delete(Long id) {
        Supplier s = findEntity(id);
        if (purchaseRepository.existsBySupplierId(id)) {
            throw new BadRequestException(
                    "Cannot delete a supplier that has recorded purchases. Delete the purchases first.");
        }
        supplierRepository.delete(s);
    }

    public Supplier findEntity(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    private void apply(Supplier s, SupplierRequest req) {
        s.setName(req.name());
        s.setContactName(req.contactName());
        s.setEmail(req.email());
        s.setPhone(req.phone());
        s.setAddress(req.address());
        s.setTaxId(req.taxId());
        s.setNotes(req.notes());
    }
}
