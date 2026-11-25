/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * 			v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * 			obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * 			the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * 			Copyright (C) Thoughtworks Inc.
 */
package org.bahmni.module.dedup.api.model;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.User;

import java.util.Date;

/**
 * Records that two patients were reviewed and are known to be distinct people. The pair is stored
 * with the smaller patient id in {@code patientIdA}, so (A, B) and (B, A) are the same record.
 */
public class PatientDuplicateSuppression extends BaseOpenmrsObject {
	
	private Integer id;
	
	private Integer patientIdA;
	
	private Integer patientIdB;
	
	private String reason;
	
	private User creator;
	
	private Date dateCreated;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public Integer getPatientIdA() {
		return patientIdA;
	}
	
	public void setPatientIdA(Integer patientIdA) {
		this.patientIdA = patientIdA;
	}
	
	public Integer getPatientIdB() {
		return patientIdB;
	}
	
	public void setPatientIdB(Integer patientIdB) {
		this.patientIdB = patientIdB;
	}
	
	public String getReason() {
		return reason;
	}
	
	public void setReason(String reason) {
		this.reason = reason;
	}
	
	public User getCreator() {
		return creator;
	}
	
	public void setCreator(User creator) {
		this.creator = creator;
	}
	
	public Date getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
}
