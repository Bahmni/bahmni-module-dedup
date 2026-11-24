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

import org.openmrs.Patient;

/**
 * Extension point for narrowing the candidates returned by {@link PatientMatchService}. Any module
 * can contribute a filter by exposing a Spring bean that implements this interface; all such beans
 * are applied in order, each receiving the output of the previous one.
 * <p>
 * Implementations must return a subset of the given candidates, preserve their order, and must not
 * modify the list that was passed in.
 */
public interface PatientMatchFilter {
	
	List<Patient> filter(List<Patient> candidates);
}
