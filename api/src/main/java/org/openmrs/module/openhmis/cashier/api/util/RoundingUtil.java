package org.openmrs.module.openhmis.cashier.api.util;

import org.apache.commons.logging.Log;
import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.openhmis.cashier.api.ICashierOptionsService;
import org.openmrs.module.openhmis.cashier.api.model.Bill;
import org.openmrs.module.openhmis.cashier.api.model.BillLineItem;
import org.openmrs.module.openhmis.cashier.api.model.CashierOptions;
import org.openmrs.module.openhmis.cashier.web.CashierWebConstants;
import org.openmrs.module.openhmis.inventory.api.IDepartmentDataService;
import org.openmrs.module.openhmis.inventory.api.IItemDataService;
import org.openmrs.module.openhmis.inventory.api.model.Department;
import org.openmrs.module.openhmis.inventory.api.model.Item;
import org.openmrs.module.openhmis.inventory.api.model.ItemPrice;

import java.math.BigDecimal;

public class RoundingUtil {
	public static BigDecimal round(BigDecimal value, BigDecimal nearest, CashierOptions.RoundingMode mode) {
		if (nearest.equals(BigDecimal.ZERO)) {
			return value;
		}
		BigDecimal factor = BigDecimal.ONE.divide(nearest);
		int scale = nearest.scale();
		switch (mode) {
			case FLOOR:
				return value.multiply(factor)
					.setScale(value.scale(), BigDecimal.ROUND_FLOOR)
					.divide(factor)
					.setScale(scale, BigDecimal.ROUND_FLOOR);
			case CEILING:
				return value.multiply(factor)
					.setScale(value.scale(), BigDecimal.ROUND_CEILING)
					.divide(factor)
					.setScale(scale, BigDecimal.ROUND_CEILING);
			default:
				return value.multiply(factor)
					.setScale(value.scale(), BigDecimal.ROUND_HALF_UP)
					.divide(factor)
					.setScale(scale, BigDecimal.ROUND_HALF_UP);
		}
	}
	
	public static void setupRoundingDeptAndItem(Log log) {
		/*
		 * Automatically add rounding item & department
		 */
		AdministrationService adminService = Context.getService(AdministrationService.class);

		String nearest = adminService.getGlobalProperty(CashierWebConstants.ROUND_TO_NEAREST_PROPERTY);
		if (nearest != null && !nearest.isEmpty() && !nearest.equals("0")) {
			MessageSourceService msgService = Context.getMessageSourceService();
			IDepartmentDataService deptService = Context.getService(IDepartmentDataService.class);
			IItemDataService itemService = Context.getService(IItemDataService.class);

			Integer itemId; 
			Integer deptId;

			deptId = parseDepartmentId(adminService);
			itemId = parseItemId(adminService);

			if (deptId == null && itemId == null) {
				Department department = new Department();
				String name = msgService.getMessage("openhmis.cashier.rounding.itemName");
				String description = msgService.getMessage("openhmis.cashier.rounding.itemDescription");
				department.setName(name);
				department.setDescription(description);
				department.setRetired(true);
				department.setRetireReason("Used by Cashier Module for rounding adjustments.");
				deptService.save(department);
				log.info("Created department for rounding item (ID = " + department.getId() + ")...");
				adminService.saveGlobalProperty(new GlobalProperty(CashierWebConstants.ROUNDING_DEPT_ID, department.getId().toString()));
				
				Item item = new Item();
				item.setName(name);
				item.setDescription(description);
				item.setDepartment(department);

				ItemPrice price = item.addPrice(name, BigDecimal.ZERO);
				item.setDefaultPrice(price);
				itemService.save(item);
				log.info("Created item for rounding (ID = " + item.getId() + ")...");
				adminService.saveGlobalProperty(new GlobalProperty(CashierWebConstants.ROUNDING_ITEM_ID, item.getId().toString()));
			}
		}	
	}

	/**
	 * Add a rounding line item to a bill if necessary
	 * @param bill
	 * @should add a rounding line item with the appropriate value
	 * @should not modify a bill that needs no rounding
	 */
	public static void addRoundingLineItem(Bill bill) {
		ICashierOptionsService cashOptService = Context.getService(ICashierOptionsService.class);
		CashierOptions options = cashOptService.getOptions();
		if (options.getRoundToNearest().equals(BigDecimal.ZERO)) {
			return;
		}

		if (options.getRoundingItemUuid() == null) {
			throw new APIException("No rounding item specified in options. This must be set in order to use rounding for bill totals.");
		}

		BigDecimal difference = bill.getTotal().subtract(RoundingUtil.round(bill.getTotal(), options.getRoundToNearest(), options.getRoundingMode()));
		if (!difference.equals(BigDecimal.ZERO)) {
			// Get rounding item
			IItemDataService itemService = Context.getService(IItemDataService.class);
			Item roundingItem = itemService.getByUuid(options.getRoundingItemUuid());

			// Create line item for rounding item and the required amount
			BillLineItem lineItem = bill.addLineItem(roundingItem, difference.abs(), "", difference.compareTo(BigDecimal.ZERO) > 0 ? -1 : 1);

			// Put the rounding line item at the end of the order
			lineItem.setLineItemOrder(bill.getLineItems() == null ? 0 : bill.getLineItems().size() - 1);
		}
	}

	private static Integer parseItemId(AdministrationService adminService) {
		Integer itemId;
		try {
			itemId = Integer.parseInt(adminService.getGlobalProperty(CashierWebConstants.ROUNDING_ITEM_ID));
		}
		catch (NumberFormatException e) {
			itemId = null;
		}
		return itemId;
	}

	private static Integer parseDepartmentId(AdministrationService adminService) {
		Integer deptId;
		try {
			deptId = Integer.parseInt(adminService.getGlobalProperty(CashierWebConstants.ROUNDING_DEPT_ID));
		}
		catch (NumberFormatException e) {
			deptId = null;
		}
		return deptId;
	}
}
