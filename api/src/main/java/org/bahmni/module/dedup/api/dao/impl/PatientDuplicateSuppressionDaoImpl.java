/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * 			v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * 			obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * 			the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * 			Copyright (C) Thoughtworks Inc.
 */
package org.bahmni.module.dedup.api.dao.impl;

import org.bahmni.module.dedup.api.dao.PatientDuplicateSuppressionDao;
import org.bahmni.module.dedup.api.model.PatientDuplicateSuppression;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component("bahmniPatientDuplicateSuppressionDao")
public class PatientDuplicateSuppressionDaoImpl implements PatientDuplicateSuppressionDao {
	
	private final SessionFactory sessionFactory;
	
	@Autowired
	public PatientDuplicateSuppressionDaoImpl(@Qualifier("sessionFactory") SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	@Override
	public List<PatientDuplicateSuppression> findExistingSuppressions(Integer patientId, Collection<Integer> targetIds) {
		if (targetIds == null || targetIds.isEmpty()) {
			return Collections.emptyList();
		}
		return sessionFactory.getCurrentSession().createQuery(
		    "from PatientDuplicateSuppression s " + "where (s.patientIdA = :patientId and s.patientIdB in (:targetIds)) "
		            + "   or (s.patientIdB = :patientId and s.patientIdA in (:targetIds))",
		    PatientDuplicateSuppression.class).setParameter("patientId", patientId).setParameterList("targetIds", targetIds)
		        .list();
	}
	
	@Override
	public PatientDuplicateSuppression save(PatientDuplicateSuppression suppression) {
		sessionFactory.getCurrentSession().saveOrUpdate(suppression);
		return suppression;
	}
}
