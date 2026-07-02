package com.anvritai.abhay.service;

import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.inventory.InventoryUnit;
import com.anvritai.abhay.domain.inventory.Warehouse;
import com.anvritai.abhay.repository.inventory.InventoryUnitRepository;
import com.anvritai.abhay.repository.inventory.WarehouseRepository;
import org.springframework.stereotype.Service;

@Service
public class InventorySeedService {
    private final InventoryUnitRepository units;
    private final WarehouseRepository warehouses;

    public InventorySeedService(InventoryUnitRepository units, WarehouseRepository warehouses) {
        this.units = units;
        this.warehouses = warehouses;
    }

    public void seedCompany(Company company) {
        if (!units.existsByCompanyIdAndSymbolIgnoreCase(company.getId(), "NOS")) {
            InventoryUnit unit = new InventoryUnit();
            unit.setCompany(company);
            unit.setName("Numbers");
            unit.setSymbol("NOS");
            unit.setDecimalPlaces(0);
            unit.setActive(true);
            units.save(unit);
        }
        if (warehouses.findFirstByCompanyIdAndPrimaryWarehouseTrueAndActiveTrue(company.getId()).isEmpty()) {
            Warehouse warehouse = new Warehouse();
            warehouse.setCompany(company);
            warehouse.setName("Main Warehouse");
            warehouse.setCode("MAIN");
            warehouse.setPrimaryWarehouse(true);
            warehouse.setActive(true);
            warehouses.save(warehouse);
        }
    }
}
