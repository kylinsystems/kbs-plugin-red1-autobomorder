/******************************************************************************
 * Product: iDempiere Free ERP Project based on Compiere (2006)               *
 * Copyright (C) 2014 Redhuan D. Oon All Rights Reserved.                     *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *  FOR NON-COMMERCIAL DEVELOPER USE ONLY                                     *
 *  @author Redhuan D. Oon  - red1@red1.org  www.red1.org                     *
 *****************************************************************************/

package org.idempiere.component;

import java.math.BigDecimal;
import java.util.List;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics; 
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MProductBOM;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.osgi.service.event.Event;

/**
 *  @author red1
 *  To convert a Sales Order lines BOM parents to BOM children 
 */
public class OrderBOMEvent extends AbstractEventHandler{
	private static CLogger log = CLogger.getCLogger(OrderBOMEvent.class); 
	private String trxName = "";
	private PO po = null;
	@Override
	protected void initialize() { 
	//register EventTopics and TableNames
		registerTableEvent(IEventTopics.DOC_BEFORE_PREPARE, MOrder.Table_Name);
		log.info("<<Auto Bom Order EVENT IS NOW INITIALIZED>>");
		}

	@Override
	protected void doHandleEvent(Event event) {
		setPo(getPO(event));
		setTrxName(po.get_TrxName());
		String type = event.getTopic();
		//testing that it works at login
		if (type.equals(IEventTopics.DOC_BEFORE_PREPARE)) 
			if (po instanceof MOrder){
				int trigger=0;
				 MOrder order = (MOrder)po; 
				 List<MOrderLine> lines = new Query(Env.getCtx(),MOrderLine.Table_Name,MOrderLine.COLUMNNAME_C_Order_ID+"=?",trxName)
				 .setParameters(order.get_ID()).list();
				 for (MOrderLine line:lines){
					 MProduct product = new Query(Env.getCtx(),MProduct.Table_Name,MProduct.COLUMNNAME_M_Product_ID+"=?",trxName)
					 .setParameters(line.getM_Product_ID()).first();
					 if (product.isBOM()){
						 //create children lines and zeroise parent qtys
						 createChildren(order, product, line.getQtyOrdered());					  
						 line.delete(true);
						 trigger++;
					 }
				 }
				 if (trigger>0)
					 order.saveEx(trxName);
			}
	}

	private void createChildren(MOrder order, MProduct product, BigDecimal qty) {
		BigDecimal hold = qty;
		MProductBOM[] children = MProductBOM.getBOMLines(product);
		 for (MProductBOM child:children){
			 qty = hold.multiply(child.getBOMQty());
			 if (child.getM_ProductBOM().isBOM())
				 createChildren(order,(MProduct)child.getM_ProductBOM(),qty); //recursive nesting until 
			 MOrderLine newline = new MOrderLine(order);
			 newline.setM_Product_ID(child.getM_ProductBOM_ID());
			 newline.setQty(qty);
			 newline.setPrice();
			 if (child.getM_ProductBOM().isBOM())
				 continue;
			 newline.saveEx(trxName);
		 }
	}  
	
	private void setPo(PO eventPO) {
		 po = eventPO;
	}

	private void setTrxName(String get_TrxName) {
	trxName = get_TrxName;
		}
}
