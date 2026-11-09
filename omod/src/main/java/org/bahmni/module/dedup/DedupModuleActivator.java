/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.bahmni.module.dedup;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;

public class DedupModuleActivator extends BaseModuleActivator {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	/** @see ModuleActivator#willStart() */
	public void willStart() {
		log.info("Starting Bahmni Patient Dedup Module");
	}
	
	/** @see ModuleActivator#willStop() */
	public void willStop() {
		log.info("Shutting down Bahmni Patient Dedup Module");
	}
	
	@Override
	public void started() {
		super.started();
		log.info("Bahmni Patient Dedup Module Started");
	}
	
	@Override
	public void willRefreshContext() {
		super.willRefreshContext();
		log.info("Bahmni Patient Dedup Module Will refresh context");
	}
	
	@Override
	public void stopped() {
		super.stopped();
		log.info("Bahmni Patient Dedup Module Stopped");
	}
}
