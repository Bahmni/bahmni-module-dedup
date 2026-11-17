/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * 			v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * 			obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * 			the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * 			Copyright (C) Thoughtworks Inc.
 */
package org.bahmni.module.dedup.api.service;

import java.util.List;

import org.hl7.fhir.r4.model.Patient;
import org.openmrs.annotation.Authorized;
import org.openmrs.util.PrivilegeConstants;

public interface PatientMatchService {
	
	@Authorized({ PrivilegeConstants.GET_PATIENTS })
	List<Patient> findMatches(Patient patient);
}
