/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * 			v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * 			obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * 			the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * 			Copyright (C) Thoughtworks Inc.
 */
package org.bahmni.module.dedup.api.dao;

import org.bahmni.module.dedup.api.model.PatientDuplicateSuppression;

import java.util.Collection;
import java.util.List;

public interface PatientDuplicateSuppressionDao {
	
	List<PatientDuplicateSuppression> findExistingSuppressions(Integer patientId, Collection<Integer> targetIds);
	
	PatientDuplicateSuppression save(PatientDuplicateSuppression suppression);
}
